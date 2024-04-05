package fetch

import chisel3._
import chisel3.util._
import common.Consts._

class Core extends Module {
    val io = IO(new Bundle {
        val imem = Flipped(new ImemPortIO);
        val exit = Output(Bool());
    })

    val regfile = Mem(32, UInt(WORD_LEN.W));

    val pc_r = RegInit(MEM_BASE);
    pc_r := pc_r + 4.U(WORD_LEN.W);

    io.imem.addr := pc_r;
    val inst = io.imem.inst;

    io.exit := (inst === 0xdeadbeefL.U(WORD_LEN.W));

    printf(p"pc_r: 0x${Hexadecimal(pc_r)}\n");
    printf(p"inst: 0x${Hexadecimal(inst)}\n");
    printf("---------\n");
}
