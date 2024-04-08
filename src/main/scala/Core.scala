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
    val pc_r     = RegInit(MEM_BASE);
    val pc_plus4 = pc_r + 4.U(WORD_LEN.W);
    val br_flag  = Wire(Bool());
    val br_addr  = Wire(UInt(WORD_LEN.W));

    val pc_next = Mux(br_flag, br_addr, pc_plus4);
    pc_r := pc_next;

    io.imem.addr := pc_r;
    val inst = io.imem.inst;
    // ========== IF ==========

    // ========== ID ==========
    val imm_i = Cat(Fill(20, inst(31)), inst(31, 20));
    val imm_s = Cat(Fill(20, inst(31)), inst(31, 25), inst(11, 7));
    val imm_b = Cat(Fill(20, inst(31)), inst(7), inst(30, 25), inst(11, 8));

    val regfile = Mem(32, UInt(WORD_LEN.W));
    val rs1_idx = inst(19, 15);
    val rs2_idx = inst(24, 20);
    val rd_idx  = inst(11, 7);
    val rs1_data =
        Mux(rs1_idx =/= 0.U(IDX_LEN.W), regfile(rs1_idx), 0.U(WORD_LEN.W));
    val rs2_data =
        Mux(rs2_idx =/= 0.U(IDX_LEN.W), regfile(rs2_idx), 0.U(WORD_LEN.W));

    val List(func, op1_sel, op2_sel, mem_wen, rf_wen, wb_sel) = ListLookup(
      inst,
      List(ALU_X, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
      Array(
        LW    -> List(ALU_ADD, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_MEM),
        SW    -> List(ALU_ADD, OP1_RS1, OP2_IMS, MEM_WEN_S, RF_WEN_X, WB_X),
        ADD   -> List(ALU_ADD, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        ADDI  -> List(ALU_ADD, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SUB   -> List(ALU_SUB, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        AND   -> List(ALU_AND, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        OR    -> List(ALU_OR, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        XOR   -> List(ALU_XOR, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        ANDI  -> List(ALU_AND, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        ORI   -> List(ALU_OR, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        XORI  -> List(ALU_XOR, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLL   -> List(ALU_SLL, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SRL   -> List(ALU_SRL, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SRA   -> List(ALU_SRA, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLLI  -> List(ALU_SLL, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SRLI  -> List(ALU_SRL, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SRAI  -> List(ALU_SRA, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLT   -> List(ALU_SLT, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLTU  -> List(ALU_SLTU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLTI  -> List(ALU_SLT, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        SLTIU -> List(ALU_SLTU, OP1_RS1, OP2_IMI, MEM_WEN_X, RF_WEN_S, WB_ALU),
        BEQ   -> List(BR_BEQ, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
        BNE   -> List(BR_BNE, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
        BLT   -> List(BR_BLT, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
        BLTU  -> List(BR_BLTU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
        BGE   -> List(BR_BGE, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X),
        BGEU  -> List(BR_BGEU, OP1_RS1, OP2_RS2, MEM_WEN_X, RF_WEN_X, WB_X)
      )
    )

    val op1_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (op1_sel === OP1_RS1) -> rs1_data
      )
    )

    val op2_data = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (op2_sel === OP2_RS2) -> rs2_data,
        (op2_sel === OP2_IMI) -> imm_i,
        (op2_sel === OP2_IMS) -> imm_s
      )
    )
    // ========== ID ==========

    // ========== EX ==========
    val alu_out = MuxCase(
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
        (func === ALU_SLTU) -> (op1_data < op2_data).asUInt
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
    // ========== MEM ==========

    // ========== WB ==========
    val wb_data = MuxCase(
      alu_out,
      Seq(
        (wb_sel === WB_MEM) -> io.dmem.dout
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
