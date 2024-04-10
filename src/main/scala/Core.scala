package jrv

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions.{SLT, _}

import scala.collection.immutable.List

class Core extends Module {
    val io = IO(new Bundle {
        val imem = Flipped(new ImemPortIO);
        val dmem = Flipped(new DmemPortIO);
        val exit = Output(Bool());
        val gp   = Output(UInt(WORD_LEN.W));
    })

    val regfile     = Mem(32, UInt(WORD_LEN.W));
    val csr_regfile = Mem(4096, UInt(WORD_LEN.W));

    // ========== Pipeline regs ==========

    // IF/ID
    val id_reg_pc   = RegInit(0.U(WORD_LEN.W));
    val id_reg_inst = RegInit(0.U(WORD_LEN.W));

    // ID/EX
    val ex_reg_pc       = RegInit(0.U(WORD_LEN.W));
    val ex_reg_op1_data = RegInit(0.U(WORD_LEN.W));
    val ex_reg_op2_data = RegInit(0.U(WORD_LEN.W));
    val ex_reg_rs2_data = RegInit(0.U(WORD_LEN.W));
    val ex_reg_rd_idx   = RegInit(0.U(WORD_LEN.W));
    val ex_reg_rf_wen   = RegInit(0.U(WORD_LEN.W));
    val ex_reg_func     = RegInit(0.U(WORD_LEN.W));
    val ex_reg_wb_sel   = RegInit(0.U(WORD_LEN.W));
    val ex_reg_imm_i    = RegInit(0.U(WORD_LEN.W));
    val ex_reg_imm_s    = RegInit(0.U(WORD_LEN.W));
    val ex_reg_imm_b    = RegInit(0.U(WORD_LEN.W));
    val ex_reg_imm_u    = RegInit(0.U(WORD_LEN.W));
    val ex_reg_imm_z    = RegInit(0.U(WORD_LEN.W));
    val ex_reg_csr_idx  = RegInit(0.U(WORD_LEN.W));
    val ex_reg_csr_cmd  = RegInit(0.U(WORD_LEN.W));
    val ex_reg_mem_wen  = RegInit(0.U(WORD_LEN.W));

    // EX/MEM
    val mem_reg_pc       = RegInit(0.U(WORD_LEN.W));
    val mem_reg_op1_data = RegInit(0.U(WORD_LEN.W));
    val mem_reg_rs2_data = RegInit(0.U(WORD_LEN.W));
    val mem_reg_rd_idx   = RegInit(0.U(WORD_LEN.W));
    val mem_reg_alu_out  = RegInit(0.U(WORD_LEN.W));
    val mem_reg_rf_wen   = RegInit(0.U(WORD_LEN.W));
    val mem_reg_wb_sel   = RegInit(0.U(WORD_LEN.W));
    val mem_reg_imm_z    = RegInit(0.U(WORD_LEN.W));
    val mem_reg_csr_idx  = RegInit(0.U(WORD_LEN.W));
    val mem_reg_csr_cmd  = RegInit(0.U(WORD_LEN.W));
    val mem_reg_mem_wen  = RegInit(0.U(WORD_LEN.W));

    // MEM/WB
    val wb_reg_rd_idx  = RegInit(0.U(WORD_LEN.W));
    val wb_reg_rf_wen  = RegInit(0.U(WORD_LEN.W));
    val wb_reg_wb_data = RegInit(0.U(WORD_LEN.W));

    // ========== Pipeline regs ==========

    // ========== IF ==========
    val if_reg_pc   = RegInit(MEM_BASE);
    val if_pc_plus4 = if_reg_pc + 4.U(WORD_LEN.W);

    io.imem.addr := if_reg_pc;
    val if_inst = io.imem.inst; // fetch an instruction

    val ex_br_flag  = Wire(Bool());
    val ex_br_addr  = Wire(UInt(WORD_LEN.W));
    val ex_jmp_flag = Wire(Bool());
    val ex_alu_out  = Wire(UInt(WORD_LEN.W));

    val if_pc_next = MuxCase(
      if_pc_plus4,
      Seq(
        ex_br_flag          -> ex_br_addr,
        ex_jmp_flag         -> ex_alu_out,
        (if_inst === ECALL) -> csr_regfile(MTVEC)
      )
    );
    if_reg_pc := if_pc_next;

    // ========== IF ==========

    id_reg_pc   := if_reg_pc;
    id_reg_inst := Mux(ex_br_flag || ex_jmp_flag, BUBBLE, if_inst); // control hazard for IF stage

    // ========== ID ==========

    val id_inst = Mux(ex_br_flag || ex_jmp_flag, BUBBLE, id_reg_inst); // control hazard for ID stage

    val id_imm_i = Cat(Fill(20, id_inst(31)), id_inst(31, 20));
    val id_imm_s = Cat(Fill(20, id_inst(31)), id_inst(31, 25), id_inst(11, 7));
    val id_imm_b = Cat(Fill(20, id_inst(31)), id_inst(7), id_inst(30, 25), id_inst(11, 8), 0.U(1.W));
    val id_imm_j = Cat(Fill(12, id_inst(31)), id_inst(19, 12), id_inst(20), id_inst(30, 21), 0.U(1.W));
    val id_imm_u = Cat(id_inst(31, 12), 0.U(12.W));
    val id_imm_z = Cat(0.U(27.W), id_inst(19, 15));

    val id_rs1_idx  = id_inst(19, 15);
    val id_rs2_idx  = id_inst(24, 20);
    val id_rd_idx   = id_inst(11, 7);
    val id_rs1_data = Mux(id_rs1_idx =/= 0.U(IDX_LEN.W), regfile(id_rs1_idx), 0.U(WORD_LEN.W));
    val id_rs2_data = Mux(id_rs2_idx =/= 0.U(IDX_LEN.W), regfile(id_rs2_idx), 0.U(WORD_LEN.W));

    val List(id_func, id_op1_sel, id_op2_sel, id_mem_wen, id_rf_wen, id_wb_sel, id_csr_cmd) = ListLookup(
      id_inst,
      List(ALU_X, OP1_X, OP2_X, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
      Array(
        // Memory
        LW -> List(ALU_ADD, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_MEM, CSR_X),
        SW -> List(ALU_ADD, OP1_RS1, OP2_IMS, MEM_WEN_S, RF_WEN_X, WB_X, CSR_X),
        // Calculation
        ADD  -> List(ALU_ADD, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        ADDI -> List(ALU_ADD, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SUB  -> List(ALU_SUB, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        // Logic
        AND  -> List(ALU_AND, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        OR   -> List(ALU_OR, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        XOR  -> List(ALU_XOR, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        ANDI -> List(ALU_AND, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        ORI  -> List(ALU_OR, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        XORI -> List(ALU_XOR, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        // Shift
        SLL  -> List(ALU_SLL, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SRL  -> List(ALU_SRL, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SRA  -> List(ALU_SRA, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SLLI -> List(ALU_SLL, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SRLI -> List(ALU_SRL, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SRAI -> List(ALU_SRA, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        // Comparison
        SLT   -> List(ALU_SLT, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SLTU  -> List(ALU_SLTU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SLTI  -> List(ALU_SLT, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        SLTIU -> List(ALU_SLTU, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        // Branch
        BEQ  -> List(BR_BEQ, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        BNE  -> List(BR_BNE, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        BLT  -> List(BR_BLT, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        BLTU -> List(BR_BLTU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        BGE  -> List(BR_BGE, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        BGEU -> List(BR_BGEU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X, CSR_X),
        // Jump
        JAL  -> List(ALU_ADD, OP1_PC, OP2_IMJ, MEM_WEN_X, RF_WEN_S, WB_PC, CSR_X),
        JALR -> List(ALU_JALR, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_PC, CSR_X),
        // Load imm
        LUI   -> List(ALU_ADD, OP1_X, OP2_IMU, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),
        AUIPC -> List(ALU_ADD, OP1_PC, OP2_IMU, MEM_WEN_X, RF_WEN_S, WB_ALU, CSR_X),

        // Zicsr
        CSRRW  -> List(ALU_SRC1, OP1_RS1, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_W),
        CSRRS  -> List(ALU_SRC1, OP1_RS1, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_S),
        CSRRC  -> List(ALU_SRC1, OP1_RS1, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_C),
        CSRRWI -> List(ALU_SRC1, OP1_IMZ, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_W),
        CSRRSI -> List(ALU_SRC1, OP1_IMZ, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_S),
        CSRRCI -> List(ALU_SRC1, OP1_IMZ, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_C),
        ECALL  -> List(ALU_X, OP1_X, OP2_X, MEM_WEN_X, RF_WEN_X, WB_X, CSR_E)
      )
    )

    val id_op1_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (id_op1_sel === OP1_RS1) -> id_rs1_data,
        (id_op1_sel === OP1_PC)  -> id_reg_pc,
        (id_op1_sel === OP1_IMZ) -> id_imm_z
      )
    )

    val id_op2_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (id_op2_sel === OP2_RS2) -> id_rs2_data,
        (id_op2_sel === OP2_IMI) -> id_imm_i,
        (id_op2_sel === OP2_IMS) -> id_imm_s,
        (id_op2_sel === OP2_IMJ) -> id_imm_j,
        (id_op2_sel === OP2_IMU) -> id_imm_u
      )
    )

    val id_csr_idx = Mux(id_csr_cmd === CSR_E, MCAUSE, id_inst(31, 20));
    // ========== ID ==========

    ex_reg_pc       := id_reg_pc;
    ex_reg_op1_data := id_op1_data;
    ex_reg_op2_data := id_op2_data;
    ex_reg_rs2_data := id_rs2_data;
    ex_reg_rd_idx   := id_rd_idx;
    ex_reg_rf_wen   := id_rf_wen;
    ex_reg_func     := id_func;
    ex_reg_wb_sel   := id_wb_sel;
    ex_reg_imm_i    := id_imm_i;
    ex_reg_imm_s    := id_imm_s;
    ex_reg_imm_b    := id_imm_b;
    ex_reg_imm_u    := id_imm_u;
    ex_reg_imm_z    := id_imm_z;
    ex_reg_csr_idx  := id_csr_idx;
    ex_reg_csr_cmd  := id_csr_cmd;
    ex_reg_mem_wen  := id_mem_wen;

    // ========== EX ==========
    ex_alu_out := MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (ex_reg_func === ALU_ADD)  -> (ex_reg_op1_data + ex_reg_op2_data),
        (ex_reg_func === ALU_SUB)  -> (ex_reg_op1_data - ex_reg_op2_data),
        (ex_reg_func === ALU_AND)  -> (ex_reg_op1_data & ex_reg_op2_data),
        (ex_reg_func === ALU_OR)   -> (ex_reg_op1_data | ex_reg_op2_data),
        (ex_reg_func === ALU_XOR)  -> (ex_reg_op1_data ^ ex_reg_op2_data),
        (ex_reg_func === ALU_SLL)  -> (ex_reg_op1_data << ex_reg_op2_data(4, 0))(31, 0),
        (ex_reg_func === ALU_SRL)  -> (ex_reg_op1_data >> ex_reg_op2_data(4, 0)).asUInt,
        (ex_reg_func === ALU_SRA)  -> (ex_reg_op1_data.asSInt >> ex_reg_op2_data(4, 0)).asUInt,
        (ex_reg_func === ALU_SLT)  -> (ex_reg_op1_data.asSInt < ex_reg_op2_data.asSInt).asUInt,
        (ex_reg_func === ALU_SLTU) -> (ex_reg_op1_data < ex_reg_op2_data).asUInt,
        (ex_reg_func === ALU_JALR) -> ((ex_reg_op1_data + ex_reg_op2_data) & (~1.U(WORD_LEN.W)).asUInt),
        (ex_reg_func === ALU_SRC1) -> ex_reg_op1_data
      )
    );

    ex_br_flag := MuxCase(
      false.B,
      Seq(
        (ex_reg_func === BR_BEQ) -> (ex_reg_op1_data === ex_reg_op2_data),
        (ex_reg_func === BNE)    -> !(ex_reg_op1_data === ex_reg_op2_data),
        (ex_reg_func === BLT)    -> (ex_reg_op1_data.asSInt < ex_reg_op2_data.asSInt),
        (ex_reg_func === BLTU)   -> (ex_reg_op1_data < ex_reg_op2_data),
        (ex_reg_func === BGE)    -> !(ex_reg_op1_data.asSInt < ex_reg_op2_data.asSInt),
        (ex_reg_func === BGEU)   -> !(ex_reg_op1_data < ex_reg_op2_data)
      )
    )
    ex_br_addr := ex_reg_pc + ex_reg_imm_b;

    ex_jmp_flag := (ex_reg_wb_sel === WB_PC);

    // ========== EX ==========

    mem_reg_pc       := ex_reg_pc;
    mem_reg_op1_data := ex_reg_op1_data;
    mem_reg_rs2_data := ex_reg_rs2_data;
    mem_reg_rd_idx   := ex_reg_rd_idx;
    mem_reg_alu_out  := ex_alu_out;
    mem_reg_rf_wen   := ex_reg_rf_wen;
    mem_reg_wb_sel   := ex_reg_wb_sel;
    mem_reg_imm_z    := ex_reg_imm_z;
    mem_reg_csr_idx  := ex_reg_csr_idx;
    mem_reg_csr_cmd  := ex_reg_csr_cmd;
    mem_reg_mem_wen  := ex_reg_mem_wen;

    // ========== MEM ==========
    io.dmem.addr := mem_reg_alu_out;
    io.dmem.wen  := mem_reg_mem_wen;
    io.dmem.din  := mem_reg_rs2_data;

    // csr
    val csr_rdata = csr_regfile(mem_reg_csr_idx);
    val csr_wdata = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (mem_reg_csr_cmd === CSR_W) -> mem_reg_op1_data,
        (mem_reg_csr_cmd === CSR_S) -> (csr_rdata | mem_reg_op1_data),
        (mem_reg_csr_cmd === CSR_C) -> (csr_rdata & (~mem_reg_op1_data).asUInt),
        (mem_reg_csr_cmd === CSR_E) -> 11.U(WORD_LEN.W) // ECALL
      )
    )

    when(mem_reg_csr_cmd =/= CSR_X) {
        csr_regfile(mem_reg_csr_idx) := csr_wdata;
    }

    val mem_wb_data = MuxCase(
      mem_reg_alu_out,
      Seq(
        (mem_reg_wb_sel === WB_MEM) -> io.dmem.dout,
        (mem_reg_wb_sel === WB_PC)  -> (mem_reg_pc + 4.U(WORD_LEN.W)),
        (mem_reg_wb_sel === WB_CSR) -> csr_rdata
      )
    );
    // ========== MEM ==========

    wb_reg_rd_idx  := mem_reg_rd_idx;
    wb_reg_rf_wen  := mem_reg_rf_wen;
    wb_reg_wb_data := mem_wb_data;

    // ========== WB ==========

    when(wb_reg_rf_wen === RF_WEN_S) {
        regfile(wb_reg_rd_idx) := wb_reg_wb_data;
    }
    // ========== WB ==========

    // debug information
    printf("-----------\n");
    printf(p"if_reg_pc        : 0x${Hexadecimal(if_reg_pc)}\n")
    printf(p"id_reg_pc        : 0x${Hexadecimal(id_reg_pc)}\n")
    printf(p"id_reg_inst      : 0x${Hexadecimal(id_reg_inst)}\n")
    printf(p"id_inst          : 0x${Hexadecimal(id_inst)}\n")
    printf(p"id_rs1_data      : 0x${Hexadecimal(id_rs1_data)}\n")
    printf(p"id_rs2_data      : 0x${Hexadecimal(id_rs2_data)}\n")
    printf(p"exe_reg_pc       : 0x${Hexadecimal(ex_reg_pc)}\n")
    printf(p"exe_reg_op1_data : 0x${Hexadecimal(ex_reg_op1_data)}\n")
    printf(p"exe_reg_op2_data : 0x${Hexadecimal(ex_reg_op2_data)}\n")
    printf(p"exe_alu_out      : 0x${Hexadecimal(ex_alu_out)}\n")
    printf(p"mem_reg_pc       : 0x${Hexadecimal(mem_reg_pc)}\n")
    printf(p"mem_wb_data      : 0x${Hexadecimal(mem_wb_data)}\n")
    printf(p"wb_reg_wb_data   : 0x${Hexadecimal(wb_reg_wb_data)}\n")
    printf("-----------\n")

    // exit chiseltest
//    io.exit := (mem_reg_pc === 0x44.U(WORD_LEN.W));
    io.exit := (id_reg_inst === UNIMP);
    io.gp   := regfile(3);
}
