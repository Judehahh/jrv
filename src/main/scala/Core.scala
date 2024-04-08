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
    })

    // ========== IF ==========
    val inst = io.imem.inst;

    val pc_r     = RegInit(MEM_BASE);
    val pc_plus4 = pc_r + 4.U(WORD_LEN.W);

    val br_flag = Wire(Bool());
    val br_addr = Wire(UInt(WORD_LEN.W));

    val jmp_flag = inst === JAL || inst === JALR;
    val alu_out  = Wire(UInt(WORD_LEN.W));

    val pc_next = MuxCase(
      pc_plus4,
      Seq(
        br_flag  -> br_addr,
        jmp_flag -> alu_out
      )
    );
    pc_r := pc_next;

    io.imem.addr := pc_r;
    // ========== IF ==========

    // ========== ID ==========
    val imm_i = Cat(Fill(20, inst(31)), inst(31, 20));
    val imm_s = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7));
    val imm_b = Cat(Fill(20, inst(31)), inst(7), inst(30, 25), inst(11, 8));
    val imm_j = Cat(Fill(12, inst(31)), inst(19, 12), inst(20), inst(30, 21), 0.U(1.W));
    val imm_u = Cat(inst(31, 12), 0.U(12.W));
    val imm_z = Cat(0.U(27.W), inst(19, 15));

    val regfile  = Mem(32, UInt(WORD_LEN.W));
    val rs1_idx  = inst(19, 15);
    val rs2_idx  = inst(24, 20);
    val rd_idx   = inst(11, 7);
    val rs1_data = Mux(rs1_idx =/= 0.U(IDX_LEN.W), regfile(rs1_idx), 0.U(WORD_LEN.W));
    val rs2_data = Mux(rs2_idx =/= 0.U(IDX_LEN.W), regfile(rs2_idx), 0.U(WORD_LEN.W));

    val csr_regfile = Mem(4096, UInt(WORD_LEN.W));
    val csr_idx     = inst(31, 20);
    val csr_rdata   = csr_regfile(csr_idx);

    val List(func, op1_sel, op2_sel, mem_wen, rf_wen, wb_sel, csr_cmd) = ListLookup(
      inst,
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
        CSRRCI -> List(ALU_SRC1, OP1_IMZ, OP2_X, MEM_WEN_X, RF_WEN_S, WB_CSR, CSR_C)
      )
    )

    val op1_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (op1_sel === OP1_RS1) -> rs1_data,
        (op1_sel === OP1_PC)  -> pc_r
      )
    )

    val op2_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (op2_sel === OP2_RS2) -> rs2_data,
        (op2_sel === OP2_IMI) -> imm_i,
        (op2_sel === OP2_IMS) -> imm_s,
        (op2_sel === OP2_IMJ) -> imm_j,
        (op2_sel === OP2_IMU) -> imm_u
      )
    )
    // ========== ID ==========

    // ========== EX ==========
    alu_out := MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (func === ALU_ADD)  -> (op1_data + op2_data),
        (func === ALU_SUB)  -> (op1_data - op2_data),
        (func === ALU_AND)  -> (op1_data & op2_data),
        (func === ALU_OR)   -> (op1_data | op2_data),
        (func === ALU_XOR)  -> (op1_data ^ op2_data),
        (func === ALU_SLL)  -> (op1_data << op2_data(4, 0))(31, 0),
        (func === ALU_SRL)  -> (op1_data >> op2_data(4, 0)).asUInt,
        (func === ALU_SRA)  -> (op1_data.asSInt >> op2_data(4, 0)).asUInt,
        (func === ALU_SLT)  -> (op1_data.asSInt < op2_data.asSInt).asUInt,
        (func === ALU_SLTU) -> (op1_data < op2_data).asUInt,
        (func === ALU_JALR) -> ((op1_data + op2_data) & (~1.U(WORD_LEN.W)).asUInt),
        (func === ALU_SRC1) -> op1_data
      )
    );

    br_flag := MuxCase(
      false.B,
      Seq(
        (func === BR_BEQ) -> (op1_data === op2_data),
        (func === BNE)    -> !(op1_data === op2_data),
        (func === BLT)    -> (op1_data.asSInt < op2_data.asSInt),
        (func === BLTU)   -> (op1_data < op2_data),
        (func === BGE)    -> !(op1_data.asSInt < op2_data.asSInt),
        (func === BGEU)   -> !(op1_data < op2_data)
      )
    )
    br_addr := pc_r + imm_b;
    // ========== EX ==========

    // ========== MEM ==========
    io.dmem.addr := alu_out;
    io.dmem.wen  := mem_wen;
    io.dmem.din  := rs2_data;

    // csr
    val csr_wdata = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (csr_cmd === CSR_W) -> op1_data,
        (csr_cmd === CSR_S) -> (csr_rdata | op1_data),
        (csr_cmd === CSR_C) -> (csr_rdata & (~op1_data).asUInt)
      )
    )

    when(csr_cmd =/= CSR_X) {
        csr_regfile(csr_idx) := csr_wdata;
    }
    // ========== MEM ==========

    // ========== WB ==========
    val wb_data = MuxCase(
      alu_out,
      Seq(
        (wb_sel === WB_MEM) -> io.dmem.dout,
        (wb_sel === WB_PC)  -> pc_plus4,
        (wb_sel === WB_CSR) -> csr_rdata
      )
    );

    when(rf_wen === RF_WEN_S) {
        regfile(rd_idx) := wb_data;
    }
    // ========== WB ==========

    // debug information
    printf(p"pc_r       : 0x${Hexadecimal(pc_r)}\n");
    printf(p"inst       : 0x${Hexadecimal(inst)}\n");
    printf(p"rs1_idx    : $rs1_idx\n");
    printf(p"rs2_idx    : $rs2_idx\n");
    printf(p"rd_idx     : $rd_idx\n");
    printf(p"rs1_data   : 0x${Hexadecimal(rs1_data)}\n");
    printf(p"rs2_data   : 0x${Hexadecimal(rs2_data)}\n");
    printf(p"wb_data    : 0x${Hexadecimal(wb_data)}\n");
    printf(p"dmem.addr  : ${io.dmem.addr}\n");
    printf(p"dmem.wen   : ${io.dmem.wen}\n")
    printf(p"dmem.wdata : 0x${Hexadecimal(io.dmem.din)}\n")
    printf("-----------\n");

    // exit chiseltest
    io.exit := (inst === 0x22222222.U(WORD_LEN.W));
}
