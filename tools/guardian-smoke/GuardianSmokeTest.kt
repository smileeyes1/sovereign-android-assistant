package ps.hakim.phoneagent

fun main() {
    val g = HakimAgentGuardian
    val base = HakimAgentGuardian.Request("job-1", HakimAgentGuardian.Effect.READ_LOCAL, "local")
    fun expect(r: HakimAgentGuardian.Result, d: HakimAgentGuardian.Decision) {
        check(r.decision == d) { "Expected $d, got $r" }
    }
    expect(g.evaluate(base, setOf("local"), emptySet()), HakimAgentGuardian.Decision.ALLOW)
    expect(g.evaluate(base, setOf("local"), setOf("job-1")), HakimAgentGuardian.Decision.BLOCK)
    expect(g.evaluate(base.copy(adapter="unknown"), setOf("local"), emptySet()), HakimAgentGuardian.Decision.BLOCK)
    expect(g.evaluate(base.copy(effect=HakimAgentGuardian.Effect.SEND), setOf("local"), emptySet()), HakimAgentGuardian.Decision.REQUIRE_APPROVAL)
    expect(g.evaluate(base.copy(effect=HakimAgentGuardian.Effect.SEND, explicitApproval=true), setOf("local"), emptySet()), HakimAgentGuardian.Decision.REQUIRE_APPROVAL)
    expect(g.evaluate(base.copy(containsSensitiveData=true, targetIsExternal=true, explicitApproval=true), setOf("local"), emptySet()), HakimAgentGuardian.Decision.BLOCK)
    expect(g.evaluate(base.copy(idempotencyKey=""), setOf("local"), emptySet()), HakimAgentGuardian.Decision.BLOCK)
    println("PASS 7 guardian smoke assertions")
}
