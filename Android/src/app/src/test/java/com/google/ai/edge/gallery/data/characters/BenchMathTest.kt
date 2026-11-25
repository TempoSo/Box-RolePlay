package com.google.ai.edge.gallery.data.characters

import org.junit.Assert.*
import org.junit.Test
class BenchMathTest {
  @Test fun speeds() {
    assertEquals(100f, BenchMath.prefillTps(1000, 10000), 0.01f)
    assertEquals(0f, BenchMath.prefillTps(0, 10000), 0f)
    assertEquals(10f, BenchMath.decodeTps(11, 1000, 2000), 0.01f)
    assertEquals(0f, BenchMath.decodeTps(1, 1000, 2000), 0f)
    assertEquals(250, BenchMath.estimateTokens("a".repeat(1000)))
  }
  @Test fun ranking() {
    val a = BenchRow("A","GGUF",true,decodeTps=7f,prefillTps=40f,ramMb=3000,whenMs=1)
    val b = BenchRow("B","GGUF",true,decodeTps=4f,prefillTps=10f,ramMb=3500,whenMs=1,cfg="nb=64")
    val b2 = BenchRow("B","GGUF",true,decodeTps=30f,prefillTps=90f,ramMb=1000,whenMs=0,cfg="nb=512")
    val bad = BenchRow("C","GGUF",false)
    // B tiene dos configuraciones: gana la mejor (b2), no la más reciente.
    val best = BenchMath.best(listOf(a,b,b2,bad), 8000)!!
    assertEquals("B", best.model); assertEquals("nb=512", best.cfg)
    assertEquals(listOf("B","A"), BenchMath.rank(listOf(b,a,b2,bad),8000).map{it.model})
    assertNull(BenchMath.best(listOf(bad),8000))
  }
  @Test fun size() {
    assertEquals("M", BenchMath.suggestBySize(mapOf("S" to 1000,"M" to 3000,"L" to 7000), 8000))
    assertNull(BenchMath.suggestBySize(mapOf("L" to 7000), 8000))
  }
  @Test fun cfg() {
    val c = BenchCfg(threads=4, nBatch=128, nUBatch=128, flash=2, kv=3, noMmap=true, repack=false, ctx=4096)
    assertEquals(c, BenchCfg.parse(c.encode()))
    assertEquals(BenchCfg(), BenchCfg.parse(""))
    assertEquals(64, BenchCfg().with("nbatch", 64)!!.nBatch)
    assertEquals(64, BenchCfg().with("nbatch", 64)!!.nUBatch) // uBatch nunca mayor que nBatch
    assertEquals(512, BenchCfg().with("nbatch", 1024)!!.nUBatch)
    assertNull(BenchCfg().with("xx", 1))
    assertEquals(16, BenchCfg().with("hilos", 99)!!.threads)
    assertTrue(c.label().contains("sin mmap"))
  }
  @Test fun sweepAndAverage() {
    assertEquals(listOf(64,128,512), BenchMath.parseSweep("64, 128;512,128,abc"))
    assertEquals(8, BenchMath.parseSweep("1 2 3 4 5 6 7 8 9 10").size)
    val a = BenchSample(10f,4f,1000,900,100,20000,3000); val b = BenchSample(20f,6f,3000,900,120,30000,3200)
    val m = BenchMath.average(listOf(a,b))!!
    assertEquals(15f,m.prefillTps,0.01f); assertEquals(5f,m.decodeTps,0.01f); assertEquals(2000L,m.ttftMs); assertEquals(3200,m.ramMb)
    assertNull(BenchMath.average(emptyList()))
  }
}
