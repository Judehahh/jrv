package jrv

import chisel3._
import chisel3.util._
import chisel3.util.experimental.loadMemoryFromFileInline
import common.Consts._

class ImemPortIO extends Bundle {
    val addr = Input(UInt(WORD_LEN.W));
    val inst = Output(UInt(WORD_LEN.W));
}

class DmemPortIO extends Bundle {
    val addr = Input(UInt(WORD_LEN.W));
    val wen  = Input(Bool());
    val din  = Input(UInt(WORD_LEN.W));  // Dmem data in
    val dout = Output(UInt(WORD_LEN.W)); // Dmem data out
}

class Memory extends Module {
    val io = IO(new Bundle {
        val imem = new ImemPortIO();
        val dmem = new DmemPortIO();
    });

    // A 8 bits wide, 16KB memory.
    val mem = Mem(4096 * 4, UInt(8.W));

    // Load hex file into memory.
    loadMemoryFromFileInline(mem, "src/riscv/rv32mi-p-scall.hex".toString());

    // imem out
    io.imem.inst := Cat(
      mem(io.imem.addr + 3.U(WORD_LEN.W)),
      mem(io.imem.addr + 2.U(WORD_LEN.W)),
      mem(io.imem.addr + 1.U(WORD_LEN.W)),
      mem(io.imem.addr)
    )

    // dmem out
    io.dmem.dout := Cat(
      mem(io.dmem.addr + 3.U(WORD_LEN.W)),
      mem(io.dmem.addr + 2.U(WORD_LEN.W)),
      mem(io.dmem.addr + 1.U(WORD_LEN.W)),
      mem(io.dmem.addr)
    )

    // dmem in
    when(io.dmem.wen) {
        mem(io.dmem.addr + 3.U(WORD_LEN.W)) := io.dmem.din(31, 24);
        mem(io.dmem.addr + 2.U(WORD_LEN.W)) := io.dmem.din(23, 16);
        mem(io.dmem.addr + 1.U(WORD_LEN.W)) := io.dmem.din(15, 8);
        mem(io.dmem.addr)                   := io.dmem.din(7, 0);
    }
}
