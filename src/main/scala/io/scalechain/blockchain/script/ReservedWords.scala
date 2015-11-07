package io.scalechain.blockchain.script

trait ReservedWords extends ScriptOp

case class OpReserved() extends ReservedWords with InvalidScriptOpIfExecuted {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

case class OpVer() extends ReservedWords with InvalidScriptOpIfExecuted {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

case class OpVerIf() extends ReservedWords with AlwaysInvalidScriptOp {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

case class OpVerNotIf() extends ReservedWords with AlwaysInvalidScriptOp {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

case class OpReserved1() extends ReservedWords with InvalidScriptOpIfExecuted {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

case class OpReserved2() extends ReservedWords with InvalidScriptOpIfExecuted {
  override def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}

/** A case class for OP_NOP1 ~ OP_NOP10.
  *
  * @param value The number from 1 to 10.
  */
case class OpNop(val value : Int) extends ReservedWords {
  def execute(env : ScriptEnvironment): Unit = {
    assert(false);
    // TODO : Implement
  }
}
