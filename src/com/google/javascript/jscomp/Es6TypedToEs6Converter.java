/*
 * Copyright 2015 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.jscomp.Es6ToEs3Converter.ClassDeclarationMetadata;

/**
 * Conversion pass that converts ES6 type syntax code to plain ES6, currently transpiles field
 * declarations and field initializers.
 */
public class Es6TypedToEs6Converter implements NodeTraversal.Callback, HotSwapCompilerPass {
  static final DiagnosticType CANNOT_CONVERT_FIELDS = DiagnosticType.error(
      "JSC_CANNOT_CONVERT_FIELDS",
      "Can only convert class fields in declarations or simple assignments into symbols.");

  private final AbstractCompiler compiler;

  Es6TypedToEs6Converter(AbstractCompiler compiler) {
    this.compiler = compiler;
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }

  @Override
  public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }

  @Override
  public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }

  @Override
  public void visit(NodeTraversal t, Node n, Node parent) {
    if (n.getType() != Token.CLASS) {
      return;
    }
    Node classNode = n;
    ClassDeclarationMetadata metadata = ClassDeclarationMetadata.create(n, parent);
    if (metadata == null) {
      // Cannot handle due to static field initialization.
      compiler.report(JSError.make(n, CANNOT_CONVERT_FIELDS));
      return;
    }

    Node classMembers = classNode.getLastChild();

    // Find the constructor
    Node constructor = null;
    for (Node member : classMembers.children()) {
      if (member.isMemberFunctionDef() && member.getString().equals("constructor")) {
        constructor = member.getFirstChild();
      }
    }

    Preconditions.checkNotNull(constructor, "Constructor should be added by Es6ConvertSuper");

    Node classNameAccess = NodeUtil.newQName(compiler, metadata.fullClassName);
    Node memberVarInsertionPoint = null;  // To insert up front initially
    for (Node member : classMembers.children()) {
      // Functions are handled by the regular Es6ToEs3Converter
      if (!member.isMemberVariableDef() && !member.getBooleanProp(Node.COMPUTED_PROP_VARIABLE)) {
        continue;
      }
      compiler.reportCodeChange();
      member.getParent().removeChild(member);

      Node newNode;
      Node qualifiedMemberAccess =
          Es6ToEs3Converter.getQualifiedMemberAccess(compiler, member, classNameAccess,
              IR.thisNode());
      Node initializer = member.removeFirstChild();
      if (initializer != null) {
        newNode = IR.assign(qualifiedMemberAccess, initializer);
      } else {
        newNode = qualifiedMemberAccess;
      }
      newNode = NodeUtil.newExpr(newNode);
      newNode.useSourceInfoIfMissingFromForTree(member);
      if (member.isStaticMember()) {
        // Static fields are transpiled on the ctor function.
        metadata.insertStaticMember(newNode);
      } else {
        // Instance fields are transpiled to statements inside the ctor function.
        constructor.getLastChild().addChildAfter(newNode, memberVarInsertionPoint);
        memberVarInsertionPoint = newNode;
      }
    }
  }
}
