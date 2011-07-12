package com.martijndashorst.runcc.patterns.interpreter.parsergenerator.examples;

import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.Lexer;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.Parser;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.ParserTables;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.Token;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.lexer.LexerBuilder;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.parsertables.SLRParserTables;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.semantics.PrintSemantic;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.syntax.Syntax;
import com.martijndashorst.runcc.patterns.interpreter.parsergenerator.syntax.builder.SyntaxSeparation;

/**
	"Hello World" example. Checks if "Hello" is followed by "World", arbitrary whitespaces.
	This example shows how to build parser and lexer from scratch.
	
	@author Fritz Ritzberger
*/

public class HelloWorldParser
{
	private static final String [][] syntax =	{
		{ "Start", "\"Hello\"", "\"World\"" },
		{ Token.IGNORED, "`whitespaces`" },
	};
	
	public static void main(String [] args)
		throws Exception
	{
		SyntaxSeparation separation = new SyntaxSeparation(new Syntax(syntax));	// separate lexer and parser syntax
		LexerBuilder builder = new LexerBuilder(separation.getLexerSyntax(), separation.getIgnoredSymbols());	// build a Lexer
		Lexer lexer = builder.getLexer();
		lexer.setInput("\tHello \r\n\tWorld\n");	// give the lexer some very complex input :-)
		ParserTables parserTables = new SLRParserTables(separation.getParserSyntax());
		Parser parser = new Parser(parserTables);
		parser.parse(lexer, new PrintSemantic());	// start parsing with a print-semantic
	}

}
