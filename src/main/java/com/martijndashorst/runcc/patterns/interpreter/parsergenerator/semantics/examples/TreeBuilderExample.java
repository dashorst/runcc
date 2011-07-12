package com.martijndashorst.runcc.patterns.interpreter.parsergenerator.semantics.examples;

import java.io.InputStreamReader;
import java.io.Reader;

import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.Lexer;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.Parser;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.lexer.LexerBuilder;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.lexer.StandardLexerRules;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.semantics.TreeBuilderSemantic;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.syntax.Syntax;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.syntax.builder.SyntaxBuilderParserTables;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.syntax.builder.SyntaxSeparation;

/**
 * TreeBuilderSemantic example that shows the instance tree of the EBNF file
 * <i>syntax/builder/examples/SyntaxBuilder.syntax</i<.
 */

public class TreeBuilderExample {
	/** Test output of SyntaxBuilder syntax tree. */
	public static void main(String[] args) {
		try {
			Reader input = new InputStreamReader(
					Parser.class
							.getResourceAsStream("syntax/builder/examples/SyntaxBuilder.syntax"));
			Syntax syntax = new Syntax(StandardLexerRules.lexerSyntax);
			SyntaxSeparation separation = new SyntaxSeparation(syntax);
			LexerBuilder builder = new LexerBuilder(
					separation.getLexerSyntax(), separation.getIgnoredSymbols());
			Lexer lexer = builder.getLexer(input);
			Parser p = new Parser(new SyntaxBuilderParserTables());
			if (p.parse(lexer, new TreeBuilderSemantic())) {
				TreeBuilderSemantic.Node n = (TreeBuilderSemantic.Node) p
						.getResult();
				System.err.println("got result: " + n);
				System.out.println(n.toString(0));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
