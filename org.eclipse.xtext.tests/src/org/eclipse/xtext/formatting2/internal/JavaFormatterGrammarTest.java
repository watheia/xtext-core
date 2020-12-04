/*******************************************************************************
 * Copyright (c) 2020 itemis AG (http://www.itemis.com) and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.xtext.formatting2.internal;

import org.eclipse.xtext.XtextStandaloneSetup;
import org.eclipse.xtext.testing.IInjectorProvider;
import org.eclipse.xtext.testing.InjectWith;
import org.eclipse.xtext.testing.XtextRunner;
import org.eclipse.xtext.testing.formatter.FormatterTestHelper;
import org.eclipse.xtext.tests.AbstractXtextTests;
import org.eclipse.xtext.tests.XtextInjectorProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * @author Arne Deutsch - Initial contribution and API
 */
@RunWith(XtextRunner.class)
@InjectWith(XtextInjectorProvider.class)
public class JavaFormatterGrammarTest extends AbstractXtextTests {

	private static final String NL = System.lineSeparator();
	private static final String TAB = "\t";

	@Inject
	private FormatterTestHelper formatterTestHelper;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		with(XtextStandaloneSetup.class);
	}

	@Test
	public void action() {
		// @formatter:off
		assertFormattedGrammar(
				"Rule:" + NL +
				TAB + "SubRule ({ Other . source = current } op='.');"
				,
				"Rule:" + NL +
				TAB + "SubRule ({Other.source=current} op='.');"
		);
		// @formatter:on
	}

	@Test
	public void enumLiterals() {
		// @formatter:off
		assertFormattedGrammar(
				"enum StateType:" + NL +
				TAB + "FIRST|SECOND|THIRD;"
				,
				"enum StateType:" + NL +
				TAB + "FIRST | SECOND | THIRD;"
				);
		// @formatter:on
	}

	@Test
	public void enumLiteralAssignment() {
		// @formatter:off
		assertFormattedGrammar(
				"enum StateType:" + NL +
				TAB + "PSEUDO = 'cond';"
				,
				"enum StateType:" + NL +
				TAB + "PSEUDO='cond';"
				);
		// @formatter:on
	}

	@Test
	public void hiddenClause() {
		// @formatter:off
		assertFormattedGrammar(
				"Child  hidden ( WS ,  ID ):" + NL +
				TAB + "A;"
				,
				"Child hidden(WS, ID):" + NL +
				TAB + "A;"
				);
		// @formatter:on
	}

	@Test
	public void tokenCardinality() {
		// @formatter:off
		assertFormattedGrammar(
				"Real hidden(): INT ? '.' (EXT_INT | INT);"
				,
				"Real hidden():" + NL
				+ TAB + "INT? '.' (EXT_INT | INT);"
				);
		// @formatter:on
	}

	@Test
	public void unorderedGroups() {
		// @formatter:off
		assertFormattedGrammar(
				"CopyFieldNameToVariableStmt:" + NL +
				"  'FIELD-NAME-TO-VARIABLE' ((',' 'SCREEN' '=' '(' line=INT ',' column=INT ')') &" + NL +
				TAB + TAB + TAB + "(','     'VAR' '=' name=ID) &" + NL +
				TAB + TAB + TAB + "(',' 'TYPE' '='    'REPLACE')?);"
				,
				"CopyFieldNameToVariableStmt:" + NL +
				TAB + "'FIELD-NAME-TO-VARIABLE' ((',' 'SCREEN' '=' '(' line=INT ',' column=INT ')') &" + NL +
				TAB + "(',' 'VAR' '=' name=ID) &" + NL +
				TAB + "(',' 'TYPE' '=' 'REPLACE')?);"
				);
		// @formatter:on
	}

	@Test
	public void unorderedGroups2() {
		// @formatter:off
		assertFormattedGrammar(
				"CopyFieldNameToVariableStmt:" + NL +
				"	'FIELD-NAME-TO-VARIABLE'   (  (   ',' 'SCREEN' '=' '('   line=INT    ',' column=INT ')'  )   &   " + NL +
				"	  (  ',' 'VAR' '=' name=ID  )   &   " + NL +
				"	  (  ',' 'TYPE' '=' 'REPLACE'  )  ?   )  ;"
				,
				"CopyFieldNameToVariableStmt:" + NL +
				TAB + "'FIELD-NAME-TO-VARIABLE' ((',' 'SCREEN' '=' '(' line=INT ',' column=INT ')') &" + NL +
				TAB + "(',' 'VAR' '=' name=ID) &" + NL +
				TAB + "(',' 'TYPE' '=' 'REPLACE')?);"
				);
		// @formatter:on
	}

	@Test
	public void guardExpressions() {
		// @formatter:off
		assertFormattedGrammar(
				"AssignmentExpression <In, Yield> returns Expression:" + NL +
				TAB + "<Yield> YieldExpression<In>" + NL +
				TAB + "| RelationalExpression<In,Yield> (=> ({AssignmentExpression.lhs=current} op='=') rhs=AssignmentExpression<In,Yield>)?" + NL +
				";"
				,
				"AssignmentExpression <In, Yield> returns Expression:" + NL +
				TAB + "<Yield> YieldExpression<In>" + NL +
				TAB + "| RelationalExpression<In, Yield> (=> ({AssignmentExpression.lhs=current} op='=')" + NL +
				TAB + "rhs=AssignmentExpression<In, Yield>)?;"
				);
		// @formatter:on
	}

	@Test
	public void negatedGuardExpressions() {
		// @formatter:off
		assertFormattedGrammar(
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + TAB + "<Expression> body=Block<Yield>" + NL +
				TAB + "|"+ TAB + "<!Expression> body=Block<Yield>?" + NL +
				";"
				,
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + "<Expression> body=Block<Yield>" + NL +
				TAB + "| <!Expression> body=Block<Yield>?;"
				);
		// @formatter:on
	}

	@Test
	public void disjunctedGuardExpressions() {
		// @formatter:off
		assertFormattedGrammar(
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + "<Expression|Expression> body=Block<Yield>?" + NL +
				";"
				,
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + "<Expression | Expression> body=Block<Yield>?;"
				);
		// @formatter:on
	}

	@Test
	public void conjunctedGuardExpressions() {
		// @formatter:off
		assertFormattedGrammar(
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + "<Expression&Expression> body=Block<Yield>?" + NL +
				";"
				,
				"fragment FunctionBody <Yield, Expression>*:" + NL +
				TAB + "<Expression & Expression> body=Block<Yield>?;"
				);
		// @formatter:on
	}

	@Test
	public void bug287941TestLanguage() {
		// @formatter:off
		assertFormattedGrammar(
				"WhereEntry returns WhereEntry:" + NL +
				TAB + "AndWhereEntry ({OrWhereEntry.entries+=current} " + NL +
				TAB + TAB + "(\"or\" entries+=AndWhereEntry)+)?" + NL +
				";"
				,
				"WhereEntry returns WhereEntry:" + NL +
				TAB + "AndWhereEntry ({OrWhereEntry.entries+=current}" + NL +
				TAB + "(\"or\" entries+=AndWhereEntry)+)?;"
				);
		// @formatter:on
	}

	@Test
	public void codetemplates() {
		// @formatter:off
		assertFormattedGrammar(
				"Codetemplate:" + NL +
				"  name=ValidID '(' id=ID ',' description = STRING ')' 'for' " + NL +
				"        (context=[xtext::AbstractRule|ValidID] | keywordContext=STRING) " + NL +
				"    body = TemplateBodyWithQuotes" + NL +
				"  ; "
				,
				"Codetemplate:" + NL +
				TAB + "name=ValidID '(' id=ID ',' description=STRING ')' 'for'" + NL +
				TAB + "(context=[xtext::AbstractRule|ValidID] | keywordContext=STRING)" + NL +
				TAB + "body=TemplateBodyWithQuotes;"
				);
		// @formatter:on
	}

	@Test
	public void bug297105TestLanguage() {
		// @formatter:off
		assertFormattedGrammar(
				"Real hidden(): INT ? '.' (EXT_INT | INT);"
				,
				"Real hidden():" + NL +
				TAB + "INT? '.' (EXT_INT | INT);"
				);
		// @formatter:on
	}

	@Test
	public void xtextGrammarTestLanguage() {
		// @formatter:off
		assertFormattedGrammar(
				"ParserRule :" + NL +
				"(" + NL +
				TAB +   "  ^fragment?='fragment' RuleNameAndParams (wildcard?='*' | ReturnsClause?) " + NL +
				TAB + "| RuleNameAndParams ReturnsClause?" + NL +
				TAB + ")" + NL +
				TAB + "HiddenClause? ':'" + NL +
				"   " + TAB + "alternatives=Alternatives   " + NL +
				"    ';'" + NL +
				";"
				,
				"ParserRule:" + NL +
				TAB + "(^fragment?='fragment' RuleNameAndParams (wildcard?='*' | ReturnsClause?)" + NL +
				TAB + "| RuleNameAndParams ReturnsClause?)" + NL +
				TAB + "HiddenClause? ':'" + NL +
				TAB + "alternatives=Alternatives" + NL +
				TAB + "';';"
				);
		// @formatter:on
	}

	@Test
	public void qualifiedTypes() {
		// @formatter:off
		assertFormattedGrammar(
				"AType returns root::AType:" + NL +
				TAB + "'foo' {root::AType};"
				,
				"AType returns root::AType:" + NL +
				TAB + "'foo' {root::AType};"
				);
		// @formatter:on
	}

	@Test
	public void qualifiedTypes2() {
		// @formatter:off
		assertFormattedGrammar(
				"AType returns root :: AType :" + NL +
				TAB + "'foo' { root :: AType };"
				,
				"AType returns root::AType:" + NL +
				TAB + "'foo' {root::AType};"
				);
		// @formatter:on
	}

	@Test
	public void pureXbase() {
		// @formatter:off
		assertFormattedGrammar(
				"@Override " + NL +
				"XAssignment returns xbase::XExpression :" + NL +
				"	{xbase::XAssignment} feature=[types::JvmIdentifiableElement|FeatureCallID] OpSingleAssign value=XAssignment |" + NL +
				"	XConditionalExpression (" + NL +
				"		=>({xbase::XBinaryOperation.leftOperand=current} feature=[types::JvmIdentifiableElement|OpMultiAssign]) rightOperand=XAssignment" + NL +
				"	)?;"
				,
				"@Override" + NL +
				"XAssignment returns xbase::XExpression:" + NL +
				TAB + "{xbase::XAssignment} feature=[types::JvmIdentifiableElement|FeatureCallID] OpSingleAssign value=XAssignment |" + NL +
				TAB + "XConditionalExpression (=>({xbase::XBinaryOperation.leftOperand=current}" + NL +
				TAB + "feature=[types::JvmIdentifiableElement|OpMultiAssign]) rightOperand=XAssignment)?;"
				);
		// @formatter:on
	}

	@Test
	public void pureXbase2() {
		// @formatter:off
		assertFormattedGrammar(
				"XClosure returns XExpression:" + NL +
				"	=>({XClosure}" + NL +
				"	'[')" + NL +
				"		=>((declaredFormalParameters+=JvmFormalParameter (',' declaredFormalParameters+=JvmFormalParameter)*)? explicitSyntax?='|')?" + NL +
				"		expression=XExpressionInClosure" + NL +
				"	']';"
				,
				"XClosure returns XExpression:" + NL +
				TAB + "=>({XClosure}" + NL +
				TAB + "'[')" + NL +
				TAB + "=>((declaredFormalParameters+=JvmFormalParameter (',' declaredFormalParameters+=JvmFormalParameter)*)?" + NL +
				TAB + "explicitSyntax?='|')?" + NL +
				TAB + "expression=XExpressionInClosure" + NL +
				TAB + "']';"
				);
		// @formatter:on
	}

	@Test
	public void xbase() {
		// @formatter:off
		assertFormattedGrammar(
				"XMemberFeatureCall returns XExpression:" + NL +
				"	XPrimaryExpression" + NL +
				"	(=>({XAssignment.assignable=current} ('.'|explicitStatic?=\"::\") feature=[types::JvmIdentifiableElement|FeatureCallID] OpSingleAssign) value=XAssignment" + NL +
				"	|=>({XMemberFeatureCall.memberCallTarget=current} (\".\"|nullSafe?=\"?.\"|explicitStatic?=\"::\"))" + NL +
				"		('<' typeArguments+=JvmArgumentTypeReference (',' typeArguments+=JvmArgumentTypeReference)* '>')?" + NL +
				"		feature=[types::JvmIdentifiableElement|IdOrSuper] (" + NL +
				"			=>explicitOperationCall?='('" + NL +
				"				(" + NL +
				"				    memberCallArguments+=XShortClosure" + NL +
				"				  | memberCallArguments+=XExpression (',' memberCallArguments+=XExpression)*" + NL +
				"				)?" + NL +
				"			')')?" + NL +
				"			memberCallArguments+=XClosure?" + NL +
				"		)*;"
				,
				"XMemberFeatureCall returns XExpression:" + NL +
				TAB + "XPrimaryExpression" + NL +
				TAB + "(=>({XAssignment.assignable=current} ('.' | explicitStatic?=\"::\")" + NL +
				TAB + "feature=[types::JvmIdentifiableElement|FeatureCallID] OpSingleAssign) value=XAssignment" + NL +
				TAB + "| =>({XMemberFeatureCall.memberCallTarget=current} (\".\" | nullSafe?=\"?.\" | explicitStatic?=\"::\"))" + NL +
				TAB + "('<' typeArguments+=JvmArgumentTypeReference (',' typeArguments+=JvmArgumentTypeReference)* '>')?" + NL +
				TAB + "feature=[types::JvmIdentifiableElement|IdOrSuper] (=>explicitOperationCall?='('" + NL +
				TAB + "(memberCallArguments+=XShortClosure" + NL +
				TAB + "| memberCallArguments+=XExpression (',' memberCallArguments+=XExpression)*)?" + NL +
				TAB + "')')?" + NL +
				TAB + "memberCallArguments+=XClosure?)*;"
				);
		// @formatter:on
	}

	@Test
	public void xbaseWithAnnowtation() {
		// @formatter:off
		assertFormattedGrammar(
				"XAnnotation :" + NL +
				"	{XAnnotation}" + NL +
				"	'@' annotationType=[types::JvmAnnotationType | QualifiedName] (=>'('" + NL +
				"		(" + NL +
				"			elementValuePairs+=XAnnotationElementValuePair (',' elementValuePairs+=XAnnotationElementValuePair)*" + NL +
				"		|	value=XAnnotationElementValueOrCommaList" + NL +
				"		)?" + NL +
				"	')')?" + NL +
				";"
				,
				"XAnnotation:" + NL +
				TAB + "{XAnnotation}" + NL +
				TAB + "'@' annotationType=[types::JvmAnnotationType|QualifiedName] (=>'('" + NL +
				TAB + "(elementValuePairs+=XAnnotationElementValuePair (',' elementValuePairs+=XAnnotationElementValuePair)*" + NL +
				TAB + "| value=XAnnotationElementValueOrCommaList)?" + NL +
				TAB + "')')?;"
				);
		// @formatter:on
	}

	@Test
	public void xtend() {
		// @formatter:off
		assertFormattedGrammar(
				"ParameterizedTypeReferenceWithTypeArgs returns types::JvmParameterizedTypeReference:" + NL +
				"	type=[types::JvmType|QualifiedName] (" + NL +
				"		'<' arguments+=JvmArgumentTypeReference (',' arguments+=JvmArgumentTypeReference)* '>'" + NL +
				"		(=>({types::JvmInnerTypeReference.outer=current} '.') type=[types::JvmType|ValidID] (=>'<' arguments+=JvmArgumentTypeReference (',' arguments+=JvmArgumentTypeReference)* '>')?)*" + NL +
				"	)" + NL +
				";"
				,
				"ParameterizedTypeReferenceWithTypeArgs returns types::JvmParameterizedTypeReference:" + NL +
				TAB + "type=[types::JvmType|QualifiedName] ('<' arguments+=JvmArgumentTypeReference (','" + NL +
				TAB + "arguments+=JvmArgumentTypeReference)* '>'" + NL +
				TAB + "(=>({types::JvmInnerTypeReference.outer=current} '.') type=[types::JvmType|ValidID] (=>'<'" + NL +
				TAB + "arguments+=JvmArgumentTypeReference (',' arguments+=JvmArgumentTypeReference)* '>')?)*);"
				);
		// @formatter:on
	}

	@Test
	public void predicatedKeyword0() {
		// @formatter:off
		assertFormattedGrammar(
				"Rule:" + NL +
				TAB + "(  ->   'a')?;"
				,
				"Rule:" + NL +
				TAB + "(->'a')?;"
				);
		// @formatter:on
	}

	@Test
	public void predicatedKeyword1() {
		// @formatter:off
		assertFormattedGrammar(
				"Rule:" + NL +
				TAB + "(  =>   'a')?;"
				,
				"Rule:" + NL +
				TAB + "(=>'a')?;"
				);
		// @formatter:on
	}

	@Test
	public void predicatedKeyword3() {
		// @formatter:off
		assertFormattedGrammar(
				"Rule:" + NL +
				TAB + "{XReturnExpression} 'return'  ->  expression=XExpression?;"
				,
				"Rule:" + NL +
				TAB + "{XReturnExpression} 'return' ->expression=XExpression?;"
				);
		// @formatter:on
	}

	@Test
	public void bug287941TestLanguageGrammarAccess() {
		// @formatter:off
		assertFormattedGrammar(
				"AndWhereEntry returns WhereEntry:" + NL +
				"	ConcreteWhereEntry ({AndWhereEntry.entries += current}" + NL +
				"	(\"and\" entries+=ConcreteWhereEntry)+)?;"
				,
				"AndWhereEntry returns WhereEntry:" + NL +
				TAB + "ConcreteWhereEntry ({AndWhereEntry.entries+=current}" + NL +
				TAB + "(\"and\" entries+=ConcreteWhereEntry)+)?;"
				);
		// @formatter:on
	}

	@Test
	public void untilToken() {
		// @formatter:off
		assertFormattedGrammar(
				"terminal RULE_ML_COMMENT:" + NL +
				"			\"/*\"->\"*/\";"
				,
				"terminal RULE_ML_COMMENT:" + NL +
				TAB + "\"/*\"->\"*/\";"
				);
		// @formatter:on
	}

	private void assertFormattedGrammar(String input, String expectation) {
		assertFormatted("grammar a.A" + NL + NL + input, "grammar a.A" + NL + NL + expectation);
	}

	private void assertFormatted(String original, String expectation) {
		formatterTestHelper
				.assertFormatted(r -> r.setToBeFormatted(original).setExpectation(expectation).setUseSerializer(false));
	}

	public static class XtextInjectorProvider implements IInjectorProvider {
		@Override
		public Injector getInjector() {
			return new XtextStandaloneSetup().createInjectorAndDoEMFRegistration();
		}
	}
}
