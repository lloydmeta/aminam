package com.beachape.aminam.infra.authz.services;

import com.beachape.aminam.domain.authz.models.ConditionAttributes;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;

/// The single declared CEL environment shared by the validator (compile/type-check) and the
/// evaluator (run). `principal` and `resource` are open string maps, so a fact source can add
/// `resource.*` keys without a schema change; an unknown key is a runtime miss -> false. The two
/// roots are declared so a condition referencing them type-checks (an undeclared root like
/// `foo.bar` is rejected at author time). A condition must type-check to bool; no time functions
/// are declared, so conditions stay deterministic.
final class CelPolicyEnvironment {

  private CelPolicyEnvironment() {}

  static CelCompiler compiler() {
    return CelCompilerFactory.standardCelCompilerBuilder()
        .addVar(ConditionAttributes.PRINCIPAL, MapType.create(SimpleType.STRING, SimpleType.STRING))
        .addVar(ConditionAttributes.RESOURCE, MapType.create(SimpleType.STRING, SimpleType.STRING))
        .setResultType(SimpleType.BOOL)
        .build();
  }

  static CelRuntime runtime() {
    return CelRuntimeFactory.standardCelRuntimeBuilder().build();
  }
}
