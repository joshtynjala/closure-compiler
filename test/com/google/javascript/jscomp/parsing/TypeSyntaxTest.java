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

package com.google.javascript.jscomp.parsing;

import com.google.javascript.jscomp.CodePrinter;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.SourceFile;
import com.google.javascript.jscomp.testing.TestErrorManager;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.NamedType;
import com.google.javascript.rhino.jstype.SimpleSourceFile;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.testing.BaseJSTypeTestCase;

public class TypeSyntaxTest extends BaseJSTypeTestCase {

  private TestErrorManager testErrorManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    testErrorManager = new TestErrorManager();
  }

  private void expectErrors(String... errors) {
    testErrorManager.expectErrors(errors);
  }

  private void expectWarnings(String... warnings) {
    testErrorManager.expectWarnings(warnings);
  }

  public void testVariableDeclaration() {
    JSTypeExpression type = parseTypeOfVar("var foo: string = 'hello';");
    assertTypeEquals(STRING_TYPE, type);
  }

  public void testVariableDeclaration_errorIncomplete() {
    expectErrors("Parse error. Unexpected end of type expression");
    parse("var foo: = 'hello';");
  }

  public void testTypeInDocAndSyntax() {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("var /** string */ foo: string = 'hello';");
  }

  public void testFunctionParamDeclaration() {
    Node fn = parse("function foo(x: string) {\n}").getFirstChild();
    JSTypeExpression paramType = fn.getFirstChild().getNext().getFirstChild().getJSTypeExpression();
    assertTypeEquals(STRING_TYPE, paramType);
  }

  public void testFunctionParamDeclaration_defaultValue() {
    Node fn = parse("function foo(x: string = 'hello') {\n}").getFirstChild();
    JSTypeExpression paramType = fn.getFirstChild().getNext().getFirstChild().getJSTypeExpression();
    assertTypeEquals(STRING_TYPE, paramType);
  }

  public void testFunctionParamDeclaration_destructuringArray() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ',' expected");
    parse("function foo([x]: string) {}");
  }

  public void testFunctionParamDeclaration_destructuringArrayInner() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ']' expected");
    parse("function foo([x: string]) {}");
  }

  public void testFunctionParamDeclaration_destructuringObject() {
    // TODO(martinprobst): implement.
    expectErrors("Parse error. ',' expected");
    parse("function foo({x}: string) {}");
  }

  public void testFunctionParamDeclaration_arrow() {
    Node fn = parse("(x: string) => 'hello' + x;").getFirstChild().getFirstChild();
    JSTypeExpression paramType = fn.getFirstChild().getNext().getFirstChild().getJSTypeExpression();
    assertTypeEquals(STRING_TYPE, paramType);
  }

  public void testFunctionReturn() {
    Node fn = parse("function foo(): string {\n  return'hello';\n}").getFirstChild();
    JSTypeExpression returnType = fn.getJSTypeExpression();
    assertTypeEquals(STRING_TYPE, returnType);
  }

  public void testFunctionReturn_arrow() {
    Node fn = parse("(): string => 'hello';").getFirstChild().getFirstChild();
    JSTypeExpression returnType = fn.getJSTypeExpression();
    assertTypeEquals(STRING_TYPE, returnType);
  }

  public void testFunctionReturn_typeInDocAndSyntax() throws Exception {
    expectErrors("Parse error. Bad type syntax - "
        + "can only have JSDoc or inline type annotations, not both");
    parse("function /** string */ foo(): string { return 'hello'; }");
  }

  public void testCompositeType() {
    JSTypeExpression type = parseTypeOfVar("var foo: mymod.ns.Type;");
    JSType namedType = createNullableType(createNamedType("mymod.ns.Type"));
    assertTypeEquals(namedType, type);
    assertEquals(Token.NAME, type.getRoot().getType());
    assertEquals("mymod.ns.Type", type.getRoot().getString());
  }

  public void testCompositeType_trailingDot() {
    expectErrors("Parse error. 'identifier' expected");
    parse("var foo: mymod.Type.;");
  }

  public void testArrayType() {
    JSTypeExpression parsedType = parseTypeOfVar("var foo: string[];");

    JSType arrayOfString = createNullableType(createTemplatizedType(ARRAY_TYPE, STRING_TYPE));
    assertTypeEquals(arrayOfString, parsedType);
  }

  public void testArrayType_missingClose() {
    expectErrors("']' expected");
    parse("var foo: string[;");
  }

  public void testArrayType_namespaced() {
    Node varDecl = parse("var foo: mymod.ns.Type[];").getFirstChild();
    JSTypeExpression parsedType = varDecl.getFirstChild().getJSDocInfo().getType();

    JSType arrayOfTypes =
        createNullableType(createTemplatizedType(ARRAY_TYPE,
            createNullableType(createNamedType("mymod.ns.Type"))));
    assertTypeEquals(arrayOfTypes, parsedType);
  }

  private JSTypeExpression parseTypeOfVar(String expr) {
    Node varDecl = parse(expr).getFirstChild();
    return varDecl.getFirstChild().getJSTypeExpression();
  }

  public void testParameterizedType() {
    JSTypeExpression type = parseTypeOfVar("var x: my.parameterized.Type<ns.A, ns.B>;");
    JSType expectedType =
        createNullableType(createTemplatizedType(createNamedType("my.parameterized.Type"),
            createNullableType(createNamedType("ns.A")),
            createNullableType(createNamedType("ns.B"))));
    assertTypeEquals(expectedType, type);
  }

  public void testParameterizedType_empty() {
    expectErrors("Unexpected end of type expression");
    parse("var x: my.parameterized.Type<ns.A, >;");
  }

  public void testParameterizedType_trailing1() {
    expectErrors("'>' expected");
    parse("var x: my.parameterized.Type<ns.A;");
  }

  public void testParameterizedType_trailing2() {
    expectErrors("Unexpected end of type expression");
    parse("var x: my.parameterized.Type<ns.A,;");
  }

  private NamedType createNamedType(String typeName) {
    return registry.createNamedType(typeName, null, -1, -1);
  }

  private Node parse(String source) {
    CompilerOptions options = new CompilerOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT6_TYPED);
    options.setPreserveTypeAnnotations(true);
    options.setPrettyPrint(true);
    options.setLineLengthThreshold(80);
    options.setPreferSingleQuotes(true);

    Compiler compiler = new Compiler();
    compiler.setErrorManager(testErrorManager);
    compiler.initOptions(options);

    Node script = compiler.parse(SourceFile.fromCode("[test]", source));

    // Verifying that all warnings were seen
    assertTrue("Missing an error", testErrorManager.hasEncounteredAllErrors());
    assertTrue("Missing a warning", testErrorManager.hasEncounteredAllWarnings());

    if (script != null && testErrorManager.getErrorCount() == 0) {
      // if it can be parsed, it should round trip.
      String actual = new CodePrinter.Builder(script)
          .setCompilerOptions(options)
          .setTypeRegistry(compiler.getTypeRegistry())
          .build()  // does the actual printing.
          .trim();
      assertEquals(source, actual);
    }

    return script;
  }
}
