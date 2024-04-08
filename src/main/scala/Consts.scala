package common

import chisel3._
import chisel3.util._

object Consts {
    val WORD_LEN = 32;
    val IDX_LEN  = 5;
    var MEM_BASE = 0.U(WORD_LEN.W);

    val FUNC_LEN = 5;
    val ALU_X    = 0.U(FUNC_LEN.W);
    val ALU_ADD  = 1.U(FUNC_LEN.W);
    val ALU_SUB  = 2.U(FUNC_LEN.W);
    val ALU_AND  = 3.U(FUNC_LEN.W);
    val ALU_OR   = 4.U(FUNC_LEN.W);
    val ALU_XOR  = 5.U(FUNC_LEN.W);
    val ALU_SLL  = 6.U(FUNC_LEN.W);
    val ALU_SRL  = 7.U(FUNC_LEN.W);
    val ALU_SRA  = 8.U(FUNC_LEN.W);
    val ALU_SLT  = 9.U(FUNC_LEN.W);
    val ALU_SLTU = 10.U(FUNC_LEN.W);
    val ALU_JALR = 11.U(FUNC_LEN.W);
    val BR_BEQ   = 12.U(FUNC_LEN.W);
    val BR_BNE   = 13.U(FUNC_LEN.W);
    val BR_BLT   = 14.U(FUNC_LEN.W);
    val BR_BLTU  = 15.U(FUNC_LEN.W);
    val BR_BGE   = 16.U(FUNC_LEN.W);
    val BR_BGEU  = 17.U(FUNC_LEN.W);

    val OP1_LEN = 1;
    val OP1_RS1 = 0.U(OP1_LEN.W);
    val OP1_PC  = 1.U(OP1_LEN.W);

    val OP2_LEN = 2;
    val OP2_RS2 = 0.U(OP2_LEN.W);
    val OP2_IMI = 1.U(OP2_LEN.W);
    val OP2_IMS = 2.U(OP2_LEN.W);
    val OP2_IMJ = 3.U(OP2_LEN.W);

    val MEM_WEN_LEN = 1;
    val MEM_WEN_X   = 0.U(MEM_WEN_LEN.W);
    val MEM_WEN_S   = 0.U(MEM_WEN_LEN.W); // scalar

    val RF_WEN_LEN = 1;
    val RF_WEN_X   = 0.U(RF_WEN_LEN.W);
    val RF_WEN_S   = 1.U(RF_WEN_LEN.W);

    val WB_SEL_LEN = 2;
    val WB_X       = 0.U(WB_SEL_LEN.W);
    val WB_ALU     = 0.U(WB_SEL_LEN.W); // wb_data <> alu_out by default
    val WB_MEM     = 1.U(WB_SEL_LEN.W);
    val WB_PC      = 2.U(WB_SEL_LEN.W);
}
