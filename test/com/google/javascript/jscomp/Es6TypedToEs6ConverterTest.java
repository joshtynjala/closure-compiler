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

import com.google.common.base.Joiner;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;

public class Es6TypedToEs6ConverterTest extends CompilerTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setAcceptedLanguage(LanguageMode.ECMASCRIPT6_TYPED);
  }

  @Override
  protected CompilerOptions getOptions() {
    CompilerOptions options = super.getOptions();
    options.setLanguageIn(LanguageMode.ECMASCRIPT6_TYPED);
    options.setLanguageOut(LanguageMode.ECMASCRIPT6);
    return options;
  }

  @Override
  protected CompilerPass getProcessor(Compiler compiler) {
    PhaseOptimizer optimizer = new PhaseOptimizer(compiler, null, null);
    DefaultPassConfig passConfig = new DefaultPassConfig(getOptions());
    optimizer.addOneTimePass(passConfig.es6ConvertSuper);  // required for no ctor tests
    optimizer.addOneTimePass(passConfig.convertEs6TypedToEs6);
    return optimizer;
  }

  public void testMemberVariable() throws Exception {
    test(
        Joiner.on('\n').join(
            "class C {",
            "  mv: number;",
            "  mv2: number = 1;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  constructor() {",
            "    this.mv;",
            "    this.mv2 = 1;",
            "    this.f = 1;",
            "  }",
            "}"));
  }

  public void testMemberVariable_noCtor() throws Exception {
    test(
        Joiner.on('\n').join(
            "class C {",
            "  mv: number;",
            "  mv2: number = 1;",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  constructor() {",
            "    this.mv;",
            "    this.mv2 = 1;",
            "  }",
            "}"));
  }

  public void testMemberVariable_static() throws Exception {
    test(
        Joiner.on('\n').join(
            "class C {",
            "  static smv = 3;",
            "  constructor() {",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  constructor() {",
            "  }",
            "}\n",
            "C.smv = 3;"));
  }

  public void testMemberVariable_staticVarAssign() throws Exception {
    test(
        Joiner.on('\n').join(
            "var C = class {",
            "  static smv = 3;",
            "  constructor() {",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "var C = class {",
            "  constructor() {",
            "  }",
            "}\n",
            "C.smv = 3;"));
  }

  public void testMemberVariable_staticAnonymous() throws Exception {
    test(
        Joiner.on('\n').join(
            "(class {",
            "  constructor() {",
            "  }",
            "})"),
        Joiner.on('\n').join(
            "(class {",
            "  static smv = 3;",
            "  constructor() {",
            "  }",
            "})\n"),
        Es6TypedToEs6Converter.CANNOT_CONVERT_FIELDS);
    }

  public void testComputedPropertyVariable() throws Exception {
    test(
        Joiner.on('\n').join(
            "class C {",
            "  ['mv']: number;",
            "  ['mv' + 2]: number = 1;",
            "  constructor() {",
            "    this.f = 1;",
            "  }",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  constructor() {",
            "    this['mv'];",
            "    this['mv' + 2] = 1;",
            "    this.f = 1;",
            "  }",
            "}"));
  }

  public void testComputedPropertyVariable_static() throws Exception {
    test(
        Joiner.on('\n').join(
            "class C {",
            "  static ['smv' + 2]: number = 1;",
            "}"),
        Joiner.on('\n').join(
            "class C {",
            "  constructor() {}",
            "}",
            "C['smv' + 2] = 1;"));
  }
}
