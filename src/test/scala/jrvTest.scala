package jrv

import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

class HexTest extends AnyFlatSpec with ChiselScalatestTester {
    "jrv" should "work through hex" in {
        test(new Top) { c =>
            while (!c.io.exit.peek().litToBoolean) {
                c.clock.step(1);
            }
        }
    }
}
