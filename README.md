RunCC - A Java Runtime Compiler Compiler
========================================

RunCC is a new kind of parsergenerator that generates parsers and lexers at
runtime. Source generation is only optional. It features simplicity, a
standalone useable UNICODE enabled lexer, and a simple EBNF dialect for
specifying syntaxes in a File. Although intended for small languages, it comes
with Java and XML example parsers.

This package is open source, published under Lesser GNU Public License (LGPL).

This document applies to runcc 0.6. Old documentation is contained in old
download archives.

Author: Ritzberger Fritz, June 2004

 * Quick start
 * Calculator sample
 * Implementation Steps
 * Define the target language syntax
 * Implement a Semantic
 * Make the syntax available
 * Load the lexer with input
 * Parse the input
 * Using the builders
 * Parser builder
 * Lexer builder
 * Lexer services
 * Lexer token listener
 * Lexing without end-of-input
 * Three kinds of ParserTables
 * Optional source generation
 * Predefined semantics
 * ReflectSemantic
 * TreeBuilderSemantic
 * Syntax checker
 * EBNF
 * EBNF syntax symbols
 * Package summary
 * ParserTables sample dump
 * Undone
 * Other parser generators

Quick Start
-----------

A parser generator creates a parser from syntax rules. Rules are used to
describe the inner structure of a text to analyze. When analyzed by the
generated parser, the parts of the text can be processed by a semantic
implementation. In short: a parser generator is a tool to create analyzers.

RunCC is written in Java. No additional libraries (except JRE) are required.
It needs Java 2 collections (does not run on JDK 1.1 or older).

RunCC provides a bottom-up (LR) parser that can be SLR, LR or LALR. It
provides a lexer that actually is a top-down (LL) parser and can read
character (UNICODE) as well as byte input.

RunCC features simplicity and the absence of any cryptography except an EBNF
dialect.

RunCC serializes built parsers and lexers to speed up the building process
next time they are needed, so

RunCC provides source generation only as option. It can generate Java code for
ParserTables, Syntax, and Semantic skeletons.

RunCC defines a clear separation of responsibilities: Syntax/Rule, Lexer,
Parser, ParserTables, Semantic. No source fragments are possible within the
syntax notation. Responsibilities are:

 - The Syntax holding the specified language rules for lexer and parser,
 - The builders that serializes lexers and parsers after build to bridge the building process next time the parser is needed,
 - Source generation utilities,
 - The universal bottom-up Parser algorithm, loaded with
 - ParserTables that hold the syntax processing tables ("Goto", "Parse-Action"),
 - The Lexer that scans tokens from input to feed the parser and (last but not least) 
 - The Semantic that processes the parsing results

The syntax is written either embedded in Java as String arrays (representing
rules), or within a separate file, using an EBNF dialect. No source fragments
are possible within the syntax notation. This is done by Semantic
implementations.

To understand those principles look at the following (classical) Calculator
example, showing the elegance of ReflectSemantic. Try it by typing

    java -jar runcc.jar '(4+2.3) *(2 - -6) + 3*2'

or

	java -cp runcc.jar fri.patterns.interpreter.parsergenerator.examples.Calculator '(4+2.3) *(2 - -6) + 3*2'

I hope it will be 56.4 !

	public class Calculator extends ReflectSemantic
	{
	    private static String [][] rules = {
	        { "EXPRESSION",   "TERM" },
	        { "EXPRESSION",   "EXPRESSION", "'+'", "TERM" },
	        { "EXPRESSION",   "EXPRESSION", "'-'", "TERM" },
	        { "TERM",   "FACTOR", },
	        { "TERM",   "TERM", "'*'", "FACTOR" },
	        { "TERM",   "TERM", "'/'", "FACTOR" },
	        { "FACTOR",   "`number`", },
	        { "FACTOR",   "'-'", "FACTOR" },
	        { "FACTOR",   "'('", "EXPRESSION", "')'" },
	        { Token.IGNORED,   "`whitespaces`" },
	    };
    
	    public Object EXPRESSION(Object TERM)    {
	        return TERM;
	    }
	    public Object EXPRESSION(Object EXPRESSION, Object operator, Object TERM)    {
	        if (operator.equals("+"))
	            return new Double(((Double) EXPRESSION).doubleValue() + ((Double) TERM).doubleValue());
	        return new Double(((Double) EXPRESSION).doubleValue() - ((Double) TERM).doubleValue());
	    }
	    public Object TERM(Object FACTOR)    {
	        return FACTOR;
	    }
	    public Object TERM(Object TERM, Object operator, Object FACTOR)    {
	        if (operator.equals("*"))
	            return new Double(((Double) TERM).doubleValue() * ((Double) FACTOR).doubleValue());
	        return new Double(((Double) TERM).doubleValue() / ((Double) FACTOR).doubleValue());
	    }
	    public Object FACTOR(Object number)    {
	        return Double.valueOf((String) number);
	    }
	    public Object FACTOR(Object minus, Object FACTOR)    {
	        return new Double( - ((Double) FACTOR).doubleValue() );
	    }
	    public Object FACTOR(Object leftParenthesis, Object EXPRESSION, Object rightParenthesis)    {
	        return EXPRESSION;
	    }

	    public static void main(String [] args) throws Exception   {
	        String input = args[0];   // define some arithmetic expressions on commandline
	        Parser parser = new SerializedParser().get(rules, "Calculator");    // allocates a default lexer
	        boolean ok = parser.parse(input, new Calculator());
	        System.err.println("Parse return "+ok+", result: "+parser.getResult());
	    }
	}

You can find this sample in package
`fri/patterns/interpreter/parsergenerator/examples/Calculator.java.`

Implementation Steps
--------------------

Following are the steps to achieve your language implementation with RunCC.

### 1. Define the target language syntax.

Decide if the syntax is complicated and big and must be put into a separate
file, or can be embedded as String arrays within Java source.

You can write both parser and lexer rules into the syntax.

You can use the special nonterminals "token" (Java: `Token.TOKEN`) and
"ignored" (`Token.IGNORED`) as token markers to draw the line between lexer
and parser rules.

You can use a lot of predefined lexer rules by referencing them within
&#x60;backquotes&#x60;. Look at javadoc of `StandardLexerRules` class for a
list of available symbols.

You can use ".." to define character sets like A..Z (Java: `Token.UPTO`).

You can use "-" to define intersections like <code>`char` - `newline`</code>
(Java: `Token.BUTNOT`).

Following notations are possible, whereby the first is written into Java
source, the second is written into a separate file that can be read in using
`SyntaxBuilder` class (see below):

	// Embedded Java notation
	private static final String [][] rules = {
	    { Token.TOKEN, "others" },    // TOKEN defines what we want to receive
	    { Token.TOKEN, "`stringdef`" },
	    { Token.IGNORED, "`cstylecomment`" },    // IGNORED defines what we want to ignore
	    { "others", "others", "other" },
	    { "others", "other" },
	    { "other", "`char`", Token.BUTNOT, "`cstylecomment`", Token.BUTNOT, "`stringdef`" },
	};

	# EBNF notation (Extended Backus-Naur Form)
	token ::= others ;    // TOKEN defines what we want to receive
	token ::= `stringdef` ;
	ignored ::= `cstylecomment` ;    // IGNORED defines what we want to ignore
	others ::= others other ;
	others ::= other ;
	other ::= `char` - `cstylecomment` - `stringdef` ;

In EBNF notation you can use quantifiers like \*, +, ? to mark symbols as 0-n
(\*, optional list), 1-n (+, list) or 0-1 (?, optional), for every symbol on
right side of the rule.

**IMPORTANT**:  
Please do not use nonterminal symbols starting with '_', as such symbols are
used for artificial rules which get optimized, and probably you would not
receive them in the semantic module!

You need not to define "token" and "ignored".

When you mix lexer and parser rules, RunCC will try to separate lexer and parser rules automatically.

But when there are complex lexing rules that require nonterminals on the right side, it is necessary to define "token" and "ignored" to tell the LexerBuilder which rules are for the Lexer. Rules marked as "token" or "ignored" and all underlying ones will be handled by the Lexer (that is in fact implemented as top-down parser) and will never reach the Parser.

Furthermore it is possible to use the Lexer standalone, then you must mark the nonterminal of the root rule as "token".

### 2. Implement a Semantic

The Semantic interface defines one method that receives all callbacks during parsing:

    doSemantic(Rule rule, List parseResult, List resultRanges)

The `parseResult` will contain ...

When using Parser: a List of Objects as long as the count of symbols on the
right side of the rule, every `Object` is a result from an underlying
`doSemantic()` call.

When using standalone Lexer: one token text that is the result of the rule

The `resultRanges` will contain the range(s) of the token texts within input
text (e.g. for syntax-highlighting). The ranges `List` will be as long as
`parseResult` list. It contains elements of type `Token.Range`.

For convenience you can use `ReflectSemantic`. This class provides rule
recognition by reflection (`java.lang.reflect`): the nonterminal on the left
side is considered to be a method name, every symbol on the right side is
passed as `Object` argument to that method.

When the method is not found, a `fallback()` method is called. This returns
the token when it is just one, when the rule is left recursive and the first
argument is instanceof List, the second argument is added to that `List` (can
be used for automatic list aggregation). The `fallback()` is overrideable
(protected).

The implemented semantic must be passed to the `Parser` for processing:

	parser.parse(new MySemantic());

For a sample look at the `HelloWorld` example.

For standalone lexers you need to use a `LexerSemantic` implementation. The
interface method receives every evaluated rule top-down. A
`ReflectLexerSemantic` is provided in a sub-package, which works similar to
`ReflectSemantic` for Parsers. The difference is that it can provide a `Set`
of nonterminal Strings (left side of the rule) it wants to receive.

### 3. Make the syntax available

Write a Java main class that contains the embedded syntax or uses
SyntaxBuilder to read the EBNF file.

When using Java-embedded rules (2D String array), the following will do it:

    String [][] rules = ...
    Syntax syntax = new Syntax(rules);

When using an external EBNF file, the following will read it and convert it to
a Syntax object (assuming the file is in same directory as the
implementation):

    Reader syntaxInput = new InputStreamReader(MyLanguage.class.getResourceAsStream("MyLanguage.syntax"));
    SyntaxBuilder builder = new SyntaxBuilder(syntaxInput);
    Syntax syntax = builder.getSyntax();

SyntaxBuilder resolves all wildcard directives and alternative sentences
within rules to plain rules. Doing that several new rules must be constructed.
All nonterminals on the left sind of such artificial rules will start with
"_".

### 4. Load the lexer with input.

Decide if this is a lexer problem or a parser is the better choice. Most
likely you will try a lexer/parser combination first, but when there are too
many shift/reduce conflicts, a standalone lexer might do it as well (the
contained lexer can process even recursive rules, in fact it is a top-down
parser).

Assuming you defined a mixed parser and lexer syntax:

    SyntaxSeparation separation = new SyntaxSeparation(syntax);   // takes off TOKEN and IGNORED
    LexerBuilder builder = new LexerBuilder(separation.getLexerSyntax(), separation.getIgnoredSymbols());
    Lexer lexer = builder.getLexer();
    lexer.setInput("Very complicated text to analyze ...");

Assuming you defined a pure lexer syntax (this is a complicated way, in fact
you will use the lexer builder):

    SyntaxSeparation separation = new SyntaxSeparation(syntax);   // takes off TOKEN and IGNORED
    LexerBuilder builder = new LexerBuilder(separation.getLexerSyntax(), separation.getIgnoredSymbols());
    Lexer lexer = builder.getLexer();
    lexer.setTerminals(separation.getTokenSymbols());
    lexer.setInput("Very complicated text to analyze ...");

### 5. Parse the input

The lexer was loaded with the input to parse, it represents the input of the
parser.

When using a parser-lexer combination, create the parser and parse:

    ParserTables parserTables = new LALRParserTables(separation.getParserSyntax());
    Parser parser = new Parser(parserTables);
    parser.parse(lexer, new MySemantic());    // here the missed setTerminals() happens

When using a standalone lexer, you can lex using two different methods.

You just want to receive tokens and output certain token texts (like in
C-style-comment-strip example):

    Token token;
    do    {
        token = lexer.getNextToken(null);    // null: no hints what is expected
        if (token.symbol == null)
            lexer.dump(System.err);
        else
        if (token.text != null)
            System.out.write(token.text.toString());
    }
    while (token.symbol != null && Token.isEpsilon(token) == false);

You want to parse top-down using the lexer (like in XML example), this
evaluates the input (which means the Lexer returns false if there is more
input to read!):

    LexerImpl lexer = (LexerImpl) builder.getLexer();  // need to cast to LexerImpl for lex() method
    boolean ok = lexer.lex(new MyLexerSemantic());


**IMPORTANT:**  
When studying how to use RunCC, do not miss to read the examples. RunCC can be
used in a lot of different ways for special purposes. The more complicated the
problem, the deeper you will have to go into the framework. There are several
examples packages, the most important is
`fri.patterns.interpreter.parsergenerator.examples`.

Using the builders
------------------

### Parser builder

To speed up the building of parsers and to write some of those standard
building sequences shorter you can use the builders in package
fri/patterns/interpreter/parsergenerator/builders.

Lets look at "HelloWorld" example. Here is the first version of
HelloWorld.java without builder (this simple Syntax can be done by simple
SLRParserTables, normally you need not define the ParserTables class).

    private static final String [][] syntax =    {
        { "Start", "\"Hello\"", "\"World\"" },
        { Token.IGNORED, "`whitespaces`" },
    };
    
    public static void main(String [] args) throws Exception {
        // separate parser and lexer rules
         SyntaxSeparation separation = new SyntaxSeparation(new Syntax(syntax));
        // build the lexer from the lexer rules part
        LexerBuilder builder = new LexerBuilder(separation.getLexerSyntax(), separation.getIgnoredSymbols());
        Lexer lexer = builder.getLexer("\tHello \r\n\tWorld\n");    // the text to parse
        // build the parser from the parser rules part
        Parser parser = new Parser(new SLRParserTables(separation.getParserSyntax()));
        parser.parse(lexer, new PrintSemantic());
    }

To write this shorter and to speed up loading, a builder is used in
HelloWorld2.java. This version lasts a little longer at the first time, but
loads much faster at any further time.

    public static void main(String [] args) throws Exception {
        // build SLRParserTables, this is the simplest one, default is LALRParserTables
        Parser parser = new SerializedParser().get(SLRParserTables.class, syntax, "HelloWorld2");
        // generates "$HOME/.friware/parsers/HelloWorld2Parser.ser"
        parser.setInput("\tHello \r\n\tWorld\n");    // there was a default Lexer provided
        parser.parse(new PrintSemantic());
    }

### Lexer builder

The following example shows how to build and use a Lexer.

    Reader syntaxInput = new InputStreamReader(XmlParser.class.getResourceAsStream("Xml.syntax"));
    Lexer lexer = new SerializedLexer().get(syntaxInput, "Xml");
    Reader parseInput = new FileReader(parseFile);
    lexer.setInput(parseInput);
    // scan the input with some LexerSemantic
     boolean ok = lexer.lex(new PrintLexerSemantic());

The PrintLexerSemantic implements LexerSemantic and communicates the rules it
wants to receives by providing a Set of nonterminal Strings (that must match
those written in the Syntax). See XmlLexer for a full example how to do this.

Lexer services
--------------

You can use the LexerImpl as top-down parser with a special semantic:
LexerSemantic, or its utility implementation LexerReflectSemantic. You will
receive the Rule and its ResultTree object in interface method, for
LexerReflectSemantic you can simply implement the wanted callback methods, the
Set of wanted nonterminal Strings will be provided automatically from those
method names.

Example (assumed there is rule like "EncodingDecl ::= IsoString;" in lexing Syntax):

	public class MyLexerSemantic extends LexerReflectSemantic
	{
	    private String encodingDecl;
	    ...

	    public void EncodingDecl(ResultTree resultTree)    {
	        // process the result
	        this.encodingDecl = resultTree.toString();    // you will get e.g. "ISO-8859-1"
	    }
	    ...
	}

### Lexer token listener

To receive every Token the Lexer has scanned, you can install a listener to
the Lexer. This is for catching comments and spaces/newlines that are marked
as ignored in the EBNF (will not reach the Parser). The start and end
Token.Range is contained within the Token object. You can associate those
ranges with the result ranges received in Semantic callbacks (where you have
the ranges list of all parsed right side objects) to reconstruct the whole
text. Token.Range implements hashCode(), equals() and Comparable to be
manageable in some Map.

You install the listener by following code:

	Lexer.TokenListener tokenListener = new Lexer.TokenListener()    {
	    public void tokenReceived(Token token, boolean ignored)    {
	        if (ignored)    {
	            System.err.println("Having received ignored token: >"+token.getText()+"<");
	        }
	    }
	};
	parser.getLexer().addTokenListener(tokenListener);
	parser.parse(new SomeSemantic());

### Lexing without end-of-input

When you want to read some input that does not provide an end-of-input mark (EOF), but might contain valid text, you can use the getNextToken(LexerSemantic) method of LexerImpl:

	LexerImpl lexer = (LexerImpl) builder.getLexer();  // need to cast to LexerImpl for getNextToken(LexerSemantic) method
	Token token = getNextToken(someLexerSemantic);
	if (token.symbol == null)
	    System.out.println("error!");
	System.out.println("Unread text starts at offset "+token.range.end.offset);

Three kinds of ParserTables
---------------------------

The provided bottom-up parser is a generic algorithm that runs with every
syntax. It is driven by a ParserTables object that represents the syntax to be
used on processing input. ParserTables contain the so-called GOTO-Table, the
PARSE-ACTION table and some helper lists.

When implementing a language that uses a parser you must to decide which kind
of parser tables you need. RunCC provides three kinds of parser tables:

### LALRParserTables

The most popular solution for bottom-up parsers, the Java parser example and
the Calculator sample use it. This is default when no ParserTables class is
passed to the parser builder.

### SLRParserTables

A small implementation for simple languages.

### LRParserTables

Creates very big tables. Not well tested, more theory than practice.

The ParserTables implementations build on each other in an objectoriented way:
SLRParserTables is the base, LRParserTables adds some functionality, and
LALRParserTables is the most specialized derivation.

Optional source generation
--------------------------

When you want to increase the parser loading performance you can generate Java source code for following parts:

### Syntax

    java fri.patterns.interpreter.parsergenerator.util.SourceGenerator MyEbnf.syntax

This commandline generates Java code representing the syntax written
within the passed .syntax file. Wildcards *+? are resolved to artificial
rules that start with "_". The result would be a file named
`MyEbnfSyntax.java`.

### ParserTables

    java fri.patterns.interpreter.parsergenerator.util.SourceGenerator LALR MyEbnf.syntax

This commandline generates Java code representing the ParserTables
implementation for the syntax written within the passed `.syntax` file. The
result would be a file named `MyEbnfParserTables.java`.

### Semantic skeleton

(code base for semantic implementations of big languages)

    java fri.patterns.interpreter.parsergenerator.util.SourceGenerator semantic MyEbnf.syntax

This commandline generates Java code that contains a do-nothing Semantic
implementation for the syntax written within the passed .syntax file. The
result would be a file named MyEbnfSemantic.java, that (when compiled and
passed to the parser) just prints out the method names and all arguments the
method is called with. If the target source file exists, it will not be
overwritten, as it could already contain a manual implementation.

When launched without arguments, the SourceGenerator outputs the following message:

    SYNTAX: java fri.patterns.interpreter.parsergenerator.util.SourceGenerator [semantic|LALR|SLR|LR] file.grammar [file.syntax ...]

Predefined semantics
--------------------

RunCC provides three Semantic implementations:

### PrintSemantic

This is a very simple helper semantic that just prints out the rule and the
parsed values of that rule. Nice for testing during development.

### ReflectSemantic

This is a semantic that assumes that rules are directly mapped to methods:

 - every nonterminal on the left side of a rule will be interpreted as a
   method name,
 - every symbol on the right side of that rule will be an argument for that
   method.

All arguments are of type Object. Look at the Calculator sample.

ReflectSemantic contains a fallback method that can be used to aggregate
lists. You need to read and test the source to understand what you can do with
that class.

### TreeBuilderSemantic

This is a semantic to build a syntax instance tree from some input (like DOM
in XML). The tree node is a simple inner class, made to be extended, or to be
iterated by some follower semantic that needs a ready-made parse tree
instance.

Syntax checker
--------------

RunCC provides a utility class to print diagnostics of an EBNF:

    java fri.patterns.interpreter.parsergenerator.util.SyntaxChecker MySyntax.syntax

It detects

 * unresolved nonterminals (nonterminals without rule)
 * singular rules (nonterminal can be substituted by its singular right symbol)
 * isolated rules (redundant, can be removed)
 * none or more than one toplevel rule

EBNF dialect
------------

Following is the EBNF of the syntax definition language that you can use when
not using embedded `String` array rules. It is described in its own syntax.

This file can be found in
`parsergenerator/syntax/builder/examples/SyntaxBuilder.syntax`, but this is
just a sample. The actual rules are embedded within
`SyntaxBuilderSemantic.java`.

Mind that you should not use a starting `'_'` for nonterminals, as artificial
rules do so (added when resolving parentheses and wildcards), and those will
be optimized. It could happen that a rule with such a nonterminal will be
removed before building the parser, which has the effect that the associated
semantic never will be called.

<pre>
syntax    ::=    rule +    // a syntax consists of one or more rules
    ;

set    ::=    `bnf_chardef` ".." `bnf_chardef`    // character set definition
    ;

intersectionstartunit    ::=    set | `identifier` | `ruleref`    // intersection of character sets
    ;
intersectionunit    ::=    `bnf_chardef` | `stringdef` | intersectionstartunit
    ;
intersection    ::=    intersectionstartunit ('-' intersectionunit)+
    ;

sequnit    ::=    intersection | intersectionunit | '(' unionseq ')'     // unit of a sequence
    ;
quantifiedsequnit    ::=    sequnit `quantifier` | sequnit    // unit can be quantified
    ;
sequence    ::=    quantifiedsequnit *    // sequence of units with significant order
    ;

unionseq    ::=    sequence ('|' sequence)*    // rule alternatives
    ;
rule    ::=    `identifier` "::=" unionseq ';'    // syntax rule
    ;

ignored ::= `comment` | `whitespaces`
    ;
</pre>

EBNF syntax symbols
-------------------

Following symbols can be used within this language (as described above):

<table>
	<tr>
		<th>Symbol</th>
		<th>Meaning</th>
	</tr>
	<tr>
		<td>..</td>
		<td>For character set definitions, Java symbol is Token.UPTO, for example 'A' .. 'Z'</td>
	</tr>
	<tr>
		<td>-</td>
		<td>For character set intersections (set constraints), Java symbol is Token.BUTNOT, for example `char` - `newline`</td>
	</tr>
	<tr>
		<td>`</td>
		<td>The backquote (a UNIX shell reminiscence) is used to mark predefined lexer rules, these are implemented as String arrays within StandardLexerRules . Using `whitespaces` will import all rules describing whitespaces into the syntax. At the time there is no possibility to write your own importable lexer rules.</td>
	</tr>
	<tr>
		<td>"</td>
		<td>Double quote is used to mark string literal like "for" or "if". It can also be used for characters: "c" is equal to 'c'.</td>
	</tr>
	<tr>
		<td>'</td>
		<td>Single quote is used to mark characters or escape sequences like 'A' or '\n'. But you can NOT write 'string'!</td>
	</tr>
	<tr>
		<td>10</td>
		<td>Numbers express characters. The decimal number 10 would be \\n. Stay within UNICODE range!</td>
	</tr>
	<tr>
		<td>0xA</td>
		<td>A hexadecimal number, expressing the character \\n.</td>
	</tr>
	<tr>
		<td>012</td>
		<td>An octal number (starting with zero), expressing the character \\n.</td>
	</tr>
	<tr>
		<td>::=</td>
		<td>The original EBNF symbol to express "derives to".</td>
	</tr>
	<tr>
		<td>|</td>
		<td>The pipe expresses "or", used for alternative rules: "a ::= b | c ;" means "a" derives to "b" or "c". Will be resolved to "a ::= b; a ::= c;"</td>
	</tr>
	<tr>
		<td>;</td>
		<td>The rule (or rule alternation) terminator.</td>
	</tr>
	<tr>
		<td>()</td>
		<td>Parenthesis are grouping markers. Such a group can be quantified by *+?, e.g. "(`char` - `newline`)*".</td>
	</tr>
	<tr>
		<td>*</td>
		<td>Star marks a symbol or group that can repeat 0-n times ("optional list").</td>
	</tr>
	<tr>
		<td>+</td>
		<td>Plus marks a symbol/group that can repeat 1-n times ("list").</td>
	</tr>
	<tr>
		<td>?</td>
		<td>Question mark is used to express 0-1 occurences of a symbol/group ("optional").</td>
	</tr>
	<tr>
		<td>/* ... */</td>
		<td>Comment marker, like in Java or C/C++.</td>
	</tr>
	<tr>
		<td>// </td>
		<td>Comment marker</td>
	</tr>
	<tr>
		<td>#</td>
		<td>Comment marker</td>
	</tr>
</table>

Package summary
---------------

This is a short introduction into the contents of every package contained in RunCC.

 - fri/patterns/interpreter/parsergenerator
   - Contains toplevel classes like the universal bottom-up Parser algorithm, the Token class with constants definitions, and all interfaces to drive the parser: Lexer, ParserTables, Semantic.

 - fri/patterns/interpreter/parsergenerator/builder
   - Contains all builders that use serialization to speed up building parsers and lexers. The default source sequences to build a parser manually can be taken from here.

fri/patterns/interpreter/parsergenerator/examples
Contains examples: a Java parser, a XML lexer, a DTD lexer that imports the XML syntax, a Calculator parser for arithmetic expressions, and two HelloWorld parsers.

fri/patterns/interpreter/parsergenerator/lexer
Contains all lexer implementations and the lexer builder (not related to serialization), and the StandarLexerRules rule library.

fri/patterns/interpreter/parsergenerator/parsertables
Contains all bottom-up syntax analysis implementations like SLR, LR, LALR, building on an AbstractParserTables class that can generate source.

fri/patterns/interpreter/parsergenerator/semantics
Contains some default Semantic implementations like ReflectSemantic.

fri/patterns/interpreter/parsergenerator/syntax
Contains the Syntax and Rule classes and helpers.

fri/patterns/interpreter/parsergenerator/syntax/builder
Contains the implementations for the EBNF language, and a class to separate lexer and parser rules within a syntax.

fri/patterns/interpreter/parsergenerator/util
Contains most source generator implementations, a SyntaxChecker class to print diagnostics for an EBNF file, and helper classes.

There are some other packages containing context-specific examples.

ParserTables sample dump
------------------------

Dumping parser tables can be useful when studying the complicated ways of bottom-up parsing.
The follwing dump can be printed by writing the following Java source lines into a main class:
String [][] rules = ...;
ParserTables parserTables = new SLRParserTables(new Syntax(rules));
parserTables.dump(System.out);

The dump method lists rules, FIRST- and FOLLOW-sets, syntax nodes, and GOTO-
and PARSE/ACTION-tables:

	(Rule 0)  <START> : EXPR 
	(Rule 1)  EXPR : TERM 
	(Rule 2)  EXPR : EXPR '+' TERM 
	(Rule 3)  EXPR : EXPR '-' TERM 
	(Rule 4)  TERM : FAKT 
	(Rule 5)  TERM : TERM '*' FAKT 
	(Rule 6)  TERM : TERM '/' FAKT 
	(Rule 7)  FAKT : `NUMBER` 
	(Rule 8)  FAKT : '(' EXPR ')'
	FIRST(EXPR) = [`NUMBER`, '('] 
	FIRST(FAKT) = [`NUMBER`, '('] 
	FIRST(<START>) = [`NUMBER`, '('] 
	FIRST(TERM) = [`NUMBER`, '(']

	FOLLOW(FAKT) = [EOF, '+', '-', ')', '*', '/'] 
	FOLLOW(EXPR) = [EOF, '+', '-', ')'] 
	FOLLOW(<START>) = [EOF] 
	FOLLOW(TERM) = [EOF, '+', '-', ')', '*', '/']

	State 0 
	  (Rule 0) <START> : .EXPR  -> State 2 
	  (Rule 1) EXPR : .TERM  -> State 1 
	  (Rule 2) EXPR : .EXPR '+' TERM  -> State 2 
	  (Rule 3) EXPR : .EXPR '-' TERM  -> State 2 
	  (Rule 4) TERM : .FAKT  -> State 4 
	  (Rule 5) TERM : .TERM '*' FAKT  -> State 1 
	  (Rule 6) TERM : .TERM '/' FAKT  -> State 1 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 1 
	  (Rule 1) EXPR : TERM . 
	  (Rule 5) TERM : TERM .'*' FAKT  -> State 7 
	  (Rule 6) TERM : TERM .'/' FAKT  -> State 6

	State 2 
	  (Rule 0) <START> : EXPR . 
	  (Rule 2) EXPR : EXPR .'+' TERM  -> State 9 
	  (Rule 3) EXPR : EXPR .'-' TERM  -> State 8

	State 3 
	  (Rule 1) EXPR : .TERM  -> State 1 
	  (Rule 2) EXPR : .EXPR '+' TERM  -> State 10 
	  (Rule 3) EXPR : .EXPR '-' TERM  -> State 10 
	  (Rule 4) TERM : .FAKT  -> State 4 
	  (Rule 5) TERM : .TERM '*' FAKT  -> State 1 
	  (Rule 6) TERM : .TERM '/' FAKT  -> State 1 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : '(' .EXPR ')'  -> State 10 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 4 
	  (Rule 4) TERM : FAKT .

	State 5 
	  (Rule 7) FAKT : `NUMBER` .

	State 6 
	  (Rule 6) TERM : TERM '/' .FAKT  -> State 11 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 7 
	  (Rule 5) TERM : TERM '*' .FAKT  -> State 12 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 8 
	  (Rule 3) EXPR : EXPR '-' .TERM  -> State 13 
	  (Rule 4) TERM : .FAKT  -> State 4 
	  (Rule 5) TERM : .TERM '*' FAKT  -> State 13 
	  (Rule 6) TERM : .TERM '/' FAKT  -> State 13 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 9 
	  (Rule 2) EXPR : EXPR '+' .TERM  -> State 14 
	  (Rule 4) TERM : .FAKT  -> State 4 
	  (Rule 5) TERM : .TERM '*' FAKT  -> State 14 
	  (Rule 6) TERM : .TERM '/' FAKT  -> State 14 
	  (Rule 7) FAKT : .`NUMBER`  -> State 5 
	  (Rule 8) FAKT : .'(' EXPR ')'  -> State 3

	State 10 
	  (Rule 2) EXPR : EXPR .'+' TERM  -> State 9 
	  (Rule 3) EXPR : EXPR .'-' TERM  -> State 8 
	  (Rule 8) FAKT : '(' EXPR .')'  -> State 15

	State 11 
	  (Rule 6) TERM : TERM '/' FAKT .

	State 12 
	  (Rule 5) TERM : TERM '*' FAKT .

	State 13 
	  (Rule 3) EXPR : EXPR '-' TERM . 
	  (Rule 5) TERM : TERM .'*' FAKT  -> State 7 
	  (Rule 6) TERM : TERM .'/' FAKT  -> State 6

	State 14 
	  (Rule 2) EXPR : EXPR '+' TERM . 
	  (Rule 5) TERM : TERM .'*' FAKT  -> State 7 
	  (Rule 6) TERM : TERM .'/' FAKT  -> State 6

	State 15 
	  (Rule 8) FAKT : '(' EXPR ')' . 
 

GOTO TABLE 
----------

       |  <START>    EXPR    TERM    FAKT     '+'     '-'     '*'     '/' `NUMBER     '('     ')' 
       +------------------------------------------------------------------------------------------
     0 |        -       2       1       4       -       -       -       -       5       3       - 
     1 |        -       -       -       -       -       -       7       6       -       -       - 
     2 |        -       -       -       -       9       8       -       -       -       -       - 
     3 |        -      10       1       4       -       -       -       -       5       3       - 
     4 |        -       -       -       -       -       -       -       -       -       -       - 
     5 |        -       -       -       -       -       -       -       -       -       -       - 
     6 |        -       -       -      11       -       -       -       -       5       3       - 
     7 |        -       -       -      12       -       -       -       -       5       3       - 
     8 |        -       -      13       4       -       -       -       -       5       3       - 
     9 |        -       -      14       4       -       -       -       -       5       3       - 
    10 |        -       -       -       -       9       8       -       -       -       -      15 
    11 |        -       -       -       -       -       -       -       -       -       -       - 
    12 |        -       -       -       -       -       -       -       -       -       -       - 
    13 |        -       -       -       -       -       -       7       6       -       -       - 
    14 |        -       -       -       -       -       -       7       6       -       -       - 
    15 |        -       -       -       -       -       -       -       -       -       -       -

PARSE-ACTION TABLE 
------------------

       |      '+'     '-'     '*'     '/' `NUMBER     '('     ')'   <EOF> 
       +------------------------------------------------------------------
     0 |        -       -       -       -      SH      SH       -       - 
     1 |        1       1      SH      SH       -       -       1       1 
     2 |       SH      SH       -       -       -       -       -      AC 
     3 |        -       -       -       -      SH      SH       -       - 
     4 |        4       4       4       4       -       -       4       4 
     5 |        7       7       7       7       -       -       7       7 
     6 |        -       -       -       -      SH      SH       -       - 
     7 |        -       -       -       -      SH      SH       -       - 
     8 |        -       -       -       -      SH      SH       -       - 
     9 |        -       -       -       -      SH      SH       -       - 
    10 |       SH      SH       -       -       -       -      SH       - 
    11 |        6       6       6       6       -       -       6       6 
    12 |        5       5       5       5       -       -       5       5 
    13 |        3       3      SH      SH       -       -       3       3 
    14 |        2       2      SH      SH       -       -       2       2 
    15 |        8       8       8       8       -       -       8       8 
  


Undone
------

The parser (and the lexer) breaks at the first syntax error. No error recovery
is implemented. Start implementing at Parser.recover().

There is no "import" statement in the EBNF dialect. This would be useful for
syntaxes that use other syntaxes, like DTD that uses XML. At the time such
syntaxes must be associated programmatically by Syntax.resolveFrom() , quite
inflexible.

There is no graphical userinterface. It would be nice to have a syntax editor,
and a panel that shows a syntax-highlighting test view. Syntax-highlighting
would be easy with RunCC, as you only need a new EBFN file instead of a
syntax-plugin.

Requirements like "read next 100 characters" can not be expressed by the
current EBNF dialect. This is needed for byte formats (first 10 bytes of this,
then 40 bytes of that ...). The way to express repetitions is the list
aggregation (left recursion), but its repetition can not be limited without a
major change of lexer implementation.

Reorganize StandardLexerRules.java, divide the monolithic arrays into smaller
units.

JUnit test cases

Writing XML instead of the EBNF dialect to define a syntax

Other parser generators
-----------------------

Thank god - nobody is alone!

 - JavaCC
 - Cup
 - SableCC
 - ANTLR
 - Grammatica
 - and many more ...




Author: Ritzberger Fritz, June 2004
