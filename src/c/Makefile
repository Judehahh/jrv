%: %.c
	riscv64-linux-gnu-gcc -O2 -march=rv32iv -mabi=ilp32 -c -o $@.o $<
	riscv64-linux-gnu-ld -b elf32-littleriscv $@.o -T link.ld -o $@
	riscv64-linux-gnu-objcopy -O binary $@ $@.bin
	od -An -tx1 -w1 -v $@.bin > ../hex/$@.hex
	rm -f $@.o
	rm -f $@
	rm -f $@.bin
