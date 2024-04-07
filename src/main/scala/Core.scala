package fetch

import chisel3._
import chisel3.util._
import common.Consts._
import common.Instructions._

class Core extends Module {
    val io = IO(new Bundle {
        val imem = Flipped(new ImemPortIO);
        val dmem = Flipped(new DmemPortIO);
        val exit = Output(Bool());
    })

    // ========== IF ==========
    val pc_r = RegInit(MEM_BASE);
    pc_r := pc_r + 4.U(WORD_LEN.W);

    io.imem.addr := pc_r;
    val inst = io.imem.inst;
    // ========== IF ==========

    // ========== ID ==========
    val imm_i = Cat(Fill(20, inst(31)), inst(31, 20));
    val imm_s = Cat(Fill(20, inst(31)), Cat(inst(31, 25), inst(11, 7)));

    val regfile = Mem(32, UInt(WORD_LEN.W));
    val rs1_idx = inst(19, 15);
    val rs2_idx = inst(24, 20);
    val rd_idx  = inst(11, 7);
    val rs1_data =
        Mux(rs1_idx =/= 0.U(5.W), regfile(rs1_idx), 0.U(WORD_LEN.W));
    val rs2_data =
        Mux(rs2_idx =/= 0.U(5.W), regfile(rs2_idx), 0.U(WORD_LEN.W));
    // ========== ID ==========

    // ========== EX ==========
    val alu_out = MuxCase(
      0.U(WORD_LEN.W),
      Seq(
        (inst === LW || inst === ADDI) -> (rs1_data + imm_i),
        (inst === SW)                  -> (rs1_data + imm_s),
        (inst === ADD)                 -> (rs1_data + rs2_data),
        (inst === SUB)                 -> (rs1_data + rs2_data)
      )
    );
    // ========== EX ==========

    // ========== MEM ==========
    io.dmem.addr := alu_out;
    io.dmem.wen  := (inst === SW);
    io.dmem.din  := rs2_data;
    // ========== MEM ==========

    // ========== WB ==========
    val wb_data = MuxCase(
      alu_out,
      Seq(
        (inst === LW) -> io.dmem.dout
      )
    );

    when(inst === LW || inst === ADD || inst === ADDI || inst === SUB) {
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
