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

import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

/** Integration tests for compilation in {@link LanguageMode#ECMASCRIPT6_TYPED} mode. */
public class Es6TypedIntegrationTest extends IntegrationTestCase {

  public void testBasicTypeCheck() throws Exception {
    test(createCompilerOptions(), "var x: number = 12;\nalert(x);", "alert(12);");
  }

  public void testBasicTypeCheck_error() throws Exception {
    test(createCompilerOptions(), "var x: number = 'hello';", TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testFunctionType_correct() throws Exception {
    test(createCompilerOptions(), "function x(): number { return 12; }; alert(x());", "alert(12)");
  }

  public void testFunctionType_error() throws Exception {
    test(createCompilerOptions(), "function x(): number { return 'hello'; }",
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  public void testFunctionParameter() throws Exception {
    test(createCompilerOptions(), "function x(x: number) {}; x(12);", "");
  }

  public void testFunctionParameter_error() throws Exception {
    test(createCompilerOptions(), "function x(x: number) {}; x('hello');",
        TypeValidator.TYPE_MISMATCH_WARNING);
  }

  @Override
  CompilerOptions createCompilerOptions() {
    CompilerOptions options = new CompilerOptions();
    CompilationLevel.ADVANCED_OPTIMIZATIONS.setOptionsForCompilationLevel(options);
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT5);
    WarningLevel.VERBOSE.setOptionsForWarningLevel(options);
    return options;
  }
}
