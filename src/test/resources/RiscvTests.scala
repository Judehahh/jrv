package {package}

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class RiscvTest extends AnyFlatSpec with ChiselScalatestTester {
    "jrv" should "work through hex" in {
        test(new Top) { c =>
            while (!c.io.exit.peek().litToBoolean) {
                c.clock.step(1);
            }
            c.io.gp.expect(1);
        }
    }
}
