package common

import chisel3._
import chisel3.util._

object Consts {
    val WORD_LEN = 32;
    val IDX_LEN  = 5;
    var MEM_BASE = 0.U(WORD_LEN.W);

    // ========== Control signals ==========
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
    val ALU_SRC1 = 12.U(FUNC_LEN.W);
    val BR_BEQ   = 13.U(FUNC_LEN.W);
    val BR_BNE   = 14.U(FUNC_LEN.W);
    val BR_BLT   = 15.U(FUNC_LEN.W);
    val BR_BLTU  = 16.U(FUNC_LEN.W);
    val BR_BGE   = 17.U(FUNC_LEN.W);
    val BR_BGEU  = 18.U(FUNC_LEN.W);

    val OP1_LEN = 2;
    val OP1_X   = 0.U(OP1_LEN.W);
    val OP1_RS1 = 1.U(OP1_LEN.W);
    val OP1_PC  = 2.U(OP1_LEN.W);
    val OP1_IMZ = 3.U(OP1_LEN.W);

    val OP2_LEN = 3;
    val OP2_X   = 0.U(OP2_LEN.W);
    val OP2_RS2 = 1.U(OP2_LEN.W);
    val OP2_IMI = 2.U(OP2_LEN.W);
    val OP2_IMS = 3.U(OP2_LEN.W);
    val OP2_IMJ = 4.U(OP2_LEN.W);
    val OP2_IMU = 5.U(OP2_LEN.W);

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
    val WB_CSR     = 3.U(WB_SEL_LEN.W);

    val CSR_CMD_LEN = 3;
    val CSR_X       = 0.U(CSR_CMD_LEN.W);
    val CSR_W       = 1.U(CSR_CMD_LEN.W);
    val CSR_S       = 2.U(CSR_CMD_LEN.W);
    val CSR_C       = 3.U(CSR_CMD_LEN.W);
    val CSR_E       = 4.U(CSR_CMD_LEN.W);
    // ========== Control signals ==========

    // ========== CSRs index ==========
    val CSR_IDX_LEN = 12;
    val MSTATUS     = 0x300.U(CSR_IDX_LEN.W);
    val MTVEC       = 0x305.U(CSR_IDX_LEN.W);
    val MEPC        = 0x341.U(CSR_IDX_LEN.W);
    val MCAUSE      = 0x342.U(CSR_IDX_LEN.W);
    // ========== CSRs index ==========
}
