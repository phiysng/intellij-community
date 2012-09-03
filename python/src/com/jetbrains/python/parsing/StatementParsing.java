package com.jetbrains.python.parsing;

import com.intellij.lang.ITokenTypeRemapper;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;


/**
 * @author yole
 */
public class StatementParsing extends Parsing implements ITokenTypeRemapper {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.parsing.StatementParsing");
  @NonNls protected static final String TOK_FUTURE_IMPORT = "__future__";
  @NonNls protected static final String TOK_WITH_STATEMENT = "with_statement";
  @NonNls protected static final String TOK_NESTED_SCOPES = "nested_scopes";
  @NonNls protected static final String TOK_PRINT_FUNCTION = "print_function";
  @NonNls protected static final String TOK_WITH = "with";
  @NonNls protected static final String TOK_AS = "as";
  @NonNls protected static final String TOK_PRINT = "print";
  @NonNls protected static final String TOK_NONE = "None";
  @NonNls protected static final String TOK_TRUE = "True";
  @NonNls protected static final String TOK_DEBUG = "__debug__";
  @NonNls protected static final String TOK_FALSE = "False";
  @NonNls protected static final String TOK_NONLOCAL = "nonlocal";
  @NonNls protected static final String TOK_EXEC = "exec";

  private static final String EXPRESSION_EXPECTED = "Expression expected";
  public static final String IDENTIFIER_EXPECTED = "Identifier expected";

  protected enum Phase {NONE, FROM, FUTURE, IMPORT} // 'from __future__ import' phase

  private Phase myFutureImportPhase = Phase.NONE;
  private boolean myExpectAsKeyword = false;

  public enum FUTURE {ABSOLUTE_IMPORT, DIVISION, GENERATORS, NESTED_SCOPES, WITH_STATEMENT, PRINT_FUNCTION}

  protected Set<FUTURE> myFutureFlags = EnumSet.noneOf(FUTURE.class);

  public static class ImportTypes {
    public final IElementType statement;
    public final IElementType element;
    public IElementType starElement;

    public ImportTypes(IElementType statement, IElementType element, IElementType starElement) {
      this.statement = statement;
      this.element = element;
      this.starElement = starElement;
    }
  }

  public StatementParsing(ParsingContext context, @Nullable FUTURE futureFlag) {
    super(context);
    if (futureFlag != null) {
      myFutureFlags.add(futureFlag);
    }
  }

  public void parseStatement(ParsingScope scope) {

    while (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();
    }

    final IElementType firstToken;
    firstToken = myBuilder.getTokenType();

    if (firstToken == null) return;

    if (firstToken == PyTokenTypes.WHILE_KEYWORD) {
      parseWhileStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.IF_KEYWORD) {
      parseIfStatement(PyTokenTypes.IF_KEYWORD, PyTokenTypes.ELIF_KEYWORD, PyTokenTypes.ELSE_KEYWORD, PyElementTypes.IF_STATEMENT, scope);
      return;
    }
    if (firstToken == PyTokenTypes.FOR_KEYWORD) {
      parseForStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.TRY_KEYWORD) {
      parseTryStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.DEF_KEYWORD) {
      getFunctionParser().parseFunctionDeclaration();
      return;
    }
    if (firstToken == PyTokenTypes.AT) {
      getFunctionParser().parseDecoratedDeclaration(scope);
      return;
    }
    if (firstToken == PyTokenTypes.CLASS_KEYWORD) {
      parseClassDeclaration(scope);
      return;
    }
    if (firstToken == PyTokenTypes.WITH_KEYWORD) {
      parseWithStatement(scope);
      return;
    }

    parseSimpleStatement(scope);
  }

  protected void parseSimpleStatement(ParsingScope scope) {
    PsiBuilder builder = myContext.getBuilder();
    final IElementType firstToken = builder.getTokenType();
    if (firstToken == null) {
      return;
    }
    if (firstToken == PyTokenTypes.PRINT_KEYWORD && hasPrintStatement()) {
      parsePrintStatement(builder, scope);
      return;
    }
    if (firstToken == PyTokenTypes.ASSERT_KEYWORD) {
      parseAssertStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.BREAK_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.BREAK_STATEMENT, scope);
      return;
    }
    if (firstToken == PyTokenTypes.CONTINUE_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.CONTINUE_STATEMENT, scope);
      return;
    }
    if (firstToken == PyTokenTypes.DEL_KEYWORD) {
      parseDelStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.EXEC_KEYWORD) {
      parseExecStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.GLOBAL_KEYWORD) {
      parseNameDefiningStatement(scope, PyElementTypes.GLOBAL_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.NONLOCAL_KEYWORD) {
      parseNameDefiningStatement(scope, PyElementTypes.NONLOCAL_STATEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.IMPORT_KEYWORD) {
      parseImportStatement(scope, PyElementTypes.IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT);
      return;
    }
    if (firstToken == PyTokenTypes.FROM_KEYWORD) {
      parseFromImportStatement(scope);
      return;
    }
    if (firstToken == PyTokenTypes.PASS_KEYWORD) {
      parseKeywordStatement(builder, PyElementTypes.PASS_STATEMENT, scope);
      return;
    }
    if (firstToken == PyTokenTypes.RETURN_KEYWORD) {
      parseReturnStatement(builder, scope);
      return;
    }
    if (firstToken == PyTokenTypes.RAISE_KEYWORD) {
      parseRaiseStatement(scope);
      return;
    }
    PsiBuilder.Marker exprStatement = builder.mark();
    if (builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD) {
      getExpressionParser().parseYieldOrTupleExpression(false);
      checkEndOfStatement(scope);
      exprStatement.done(PyElementTypes.EXPRESSION_STATEMENT);
      return;
    }
    else if (getExpressionParser().parseExpressionOptional()) {
      IElementType statementType = PyElementTypes.EXPRESSION_STATEMENT;
      if (PyTokenTypes.AUG_ASSIGN_OPERATIONS.contains(builder.getTokenType())) {
        statementType = PyElementTypes.AUG_ASSIGNMENT_STATEMENT;
        builder.advanceLexer();
        if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
          builder.error(EXPRESSION_EXPECTED);
        }
      }
      else if (builder.getTokenType() == PyTokenTypes.EQ) {
        statementType = PyElementTypes.ASSIGNMENT_STATEMENT;
        exprStatement.rollbackTo();
        exprStatement = builder.mark();
        getExpressionParser().parseExpression(false, true);
        LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ, builder.getTokenType());
        builder.advanceLexer();

        while (true) {
          PsiBuilder.Marker maybeExprMarker = builder.mark();
          final boolean isYieldExpr = builder.getTokenType() == PyTokenTypes.YIELD_KEYWORD;
          if (!getExpressionParser().parseYieldOrTupleExpression(false)) {
            maybeExprMarker.drop();
            builder.error(EXPRESSION_EXPECTED);
            break;
          }
          if (builder.getTokenType() == PyTokenTypes.EQ) {
            if (isYieldExpr) {
              maybeExprMarker.drop();
              builder.error("Cannot assign to 'yield' expression");
            }
            else {
              maybeExprMarker.rollbackTo();
              getExpressionParser().parseExpression(false, true);
              LOG.assertTrue(builder.getTokenType() == PyTokenTypes.EQ, builder.getTokenType());
            }
            builder.advanceLexer();
          }
          else {
            maybeExprMarker.drop();
            break;
          }
        }
      }

      checkEndOfStatement(scope);
      exprStatement.done(statementType);
      return;
    }
    else {
      exprStatement.drop();
    }

    builder.advanceLexer();
    if (firstToken == PyTokenTypes.INCONSISTENT_DEDENT) {
      builder.error("Unindent does not match any outer indentation level");
    }
    else if (firstToken == PyTokenTypes.INDENT) {
      builder.error("Unexpected indent");
    }
    else {
      builder.error("Statement expected, found " + firstToken.toString());
    }
  }

  private boolean hasPrintStatement() {
    return myContext.getLanguageLevel().hasPrintStatement() && !myFutureFlags.contains(FUTURE.PRINT_FUNCTION);
  }

  protected void checkEndOfStatement(ParsingScope scope) {
    PsiBuilder builder = myContext.getBuilder();
    if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      builder.advanceLexer();
    }
    else if (builder.getTokenType() == PyTokenTypes.SEMICOLON) {
      if (!scope.isSuite()) {
        builder.advanceLexer();
        if (builder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
          builder.advanceLexer();
        }
      }
    }
    else if (!builder.eof()) {
      builder.error("End of statement expected");
    }
  }

  private void parsePrintStatement(final PsiBuilder builder, ParsingScope scope) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.PRINT_KEYWORD);
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() == PyTokenTypes.GTGT) {
      final PsiBuilder.Marker target = builder.mark();
      builder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      target.done(PyElementTypes.PRINT_TARGET);
    }
    else {
      getExpressionParser().parseSingleExpression(false);
    }
    while (builder.getTokenType() == PyTokenTypes.COMMA) {
      builder.advanceLexer();
      if (getEndOfStatementsTokens().contains(builder.getTokenType())) {
        break;
      }
      getExpressionParser().parseSingleExpression(false);
    }
    checkEndOfStatement(scope);
    statement.done(PyElementTypes.PRINT_STATEMENT);
  }

  protected void parseKeywordStatement(PsiBuilder builder, IElementType statementType, ParsingScope scope) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    checkEndOfStatement(scope);
    statement.done(statementType);
  }

  private void parseReturnStatement(PsiBuilder builder, ParsingScope inSuite) {
    LOG.assertTrue(builder.getTokenType() == PyTokenTypes.RETURN_KEYWORD);
    final PsiBuilder.Marker returnStatement = builder.mark();
    builder.advanceLexer();
    if (builder.getTokenType() != null && !getEndOfStatementsTokens().contains(builder.getTokenType())) {
      getExpressionParser().parseExpression();
    }
    checkEndOfStatement(inSuite);
    returnStatement.done(PyElementTypes.RETURN_STATEMENT);
  }

  private void parseDelStatement(ParsingScope inSuite) {
    assertCurrentToken(PyTokenTypes.DEL_KEYWORD);
    final PsiBuilder.Marker delStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      myBuilder.error("Expression expected");
    }
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error("Expression expected");
        }
      }
    }

    checkEndOfStatement(inSuite);
    delStatement.done(PyElementTypes.DEL_STATEMENT);
  }

  private void parseRaiseStatement(ParsingScope inSuite) {
    assertCurrentToken(PyTokenTypes.RAISE_KEYWORD);
    final PsiBuilder.Marker raiseStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getEndOfStatementsTokens().contains(myBuilder.getTokenType())) {
      getExpressionParser().parseSingleExpression(false);
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
        if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
          myBuilder.advanceLexer();
          getExpressionParser().parseSingleExpression(false);
        }
      }
      else if (myBuilder.getTokenType() == PyTokenTypes.FROM_KEYWORD) {
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myBuilder.error("Expression expected");
        }
      }
    }
    checkEndOfStatement(inSuite);
    raiseStatement.done(PyElementTypes.RAISE_STATEMENT);
  }

  private void parseAssertStatement(ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.ASSERT_KEYWORD);
    final PsiBuilder.Marker assertStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    if (getExpressionParser().parseSingleExpression(false)) {
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        if (!getExpressionParser().parseSingleExpression(false)) {
          myContext.getBuilder().error(EXPRESSION_EXPECTED);
        }
      }
      checkEndOfStatement(scope);
    }
    else {
      myContext.getBuilder().error(EXPRESSION_EXPECTED);
    }
    assertStatement.done(PyElementTypes.ASSERT_STATEMENT);
  }

  protected void parseImportStatement(ParsingScope scope, IElementType statementType, IElementType elementType) {
    final PsiBuilder builder = myContext.getBuilder();
    final PsiBuilder.Marker importStatement = builder.mark();
    builder.advanceLexer();
    parseImportElements(elementType, true, false, false);
    checkEndOfStatement(scope);
    importStatement.done(statementType);
  }

  /*
  Really parses two forms:
  from identifier import id, id... -- may be either relative or absolute
  from . import identifier -- only relative
   */
  private void parseFromImportStatement(ParsingScope inSuite) {
    PsiBuilder builder = myContext.getBuilder();
    assertCurrentToken(PyTokenTypes.FROM_KEYWORD);
    myFutureImportPhase = Phase.FROM;
    final PsiBuilder.Marker fromImportStatement = builder.mark();
    builder.advanceLexer();
    boolean from_future = false;
    boolean had_dots = parseRelativeImportDots();
    IElementType statementType = PyElementTypes.FROM_IMPORT_STATEMENT;
    if (had_dots && parseOptionalDottedName() || parseDottedName()) {
      final ImportTypes types = checkFromImportKeyword();
      statementType = types.statement;
      final IElementType elementType = types.element;
      if (myFutureImportPhase == Phase.FUTURE) {
        myFutureImportPhase = Phase.IMPORT;
        from_future = true;
      }
      if (builder.getTokenType() == PyTokenTypes.MULT) {
        final PsiBuilder.Marker star_import_mark = builder.mark();
        builder.advanceLexer();
        star_import_mark.done(types.starElement);
      }
      else if (builder.getTokenType() == PyTokenTypes.LPAR) {
        builder.advanceLexer();
        parseImportElements(elementType, false, true, from_future);
        checkMatches(PyTokenTypes.RPAR, ") expected");
      }
      else {
        parseImportElements(elementType, false, false, from_future);
      }
    }
    else if (had_dots) { // from . import ...
      final ImportTypes types = checkFromImportKeyword();
      statementType = types.statement;
      parseImportElements(types.element, false, false, from_future);
    }
    checkEndOfStatement(inSuite);
    fromImportStatement.done(statementType);
    myFutureImportPhase = Phase.NONE;
  }

  protected ImportTypes checkFromImportKeyword() {
    checkMatches(PyTokenTypes.IMPORT_KEYWORD, "'import' expected");
    return new ImportTypes(PyElementTypes.FROM_IMPORT_STATEMENT, PyElementTypes.IMPORT_ELEMENT, PyElementTypes.STAR_IMPORT_ELEMENT);
  }

  /**
   * Parses option dots before imported name.
   *
   * @return true iff there were dots.
   */
  private boolean parseRelativeImportDots() {
    PsiBuilder builder = myContext.getBuilder();
    boolean had_dots = false;
    while (builder.getTokenType() == PyTokenTypes.DOT) {
      had_dots = true;
      builder.advanceLexer();
    }
    return had_dots;
  }

  private void parseImportElements(IElementType elementType, boolean is_module_import, boolean in_parens, final boolean from_future) {
    PsiBuilder builder = myContext.getBuilder();
    while (true) {
      final PsiBuilder.Marker asMarker = builder.mark();
      if (is_module_import) { // import _
        if (!parseDottedNameAsAware(true, false)) {
          asMarker.drop();
          break;
        }
      }
      else { // from X import _
        String token_text = parseIdentifier(getReferenceType());
        if (from_future) {
          // TODO: mark all known future feature names
          if (TOK_WITH_STATEMENT.equals(token_text)) {
            myFutureFlags.add(FUTURE.WITH_STATEMENT);
          }
          else if (TOK_NESTED_SCOPES.equals(token_text)) {
            myFutureFlags.add(FUTURE.NESTED_SCOPES);
          }
          else if (TOK_PRINT_FUNCTION.equals(token_text)) {
            myFutureFlags.add(FUTURE.PRINT_FUNCTION);
          }
        }
      }
      myExpectAsKeyword = true; // possible 'as' comes as an ident; reparse it as keyword if found
      if (builder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
        builder.advanceLexer();
        myExpectAsKeyword = false;
        parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
      }
      asMarker.done(elementType);
      myExpectAsKeyword = false;
      if (builder.getTokenType() == PyTokenTypes.COMMA) {
        builder.advanceLexer();
        if (in_parens && builder.getTokenType() == PyTokenTypes.RPAR) {
          break;
        }
      }
      else {
        break;
      }
    }
  }

  @Nullable
  private String parseIdentifier(final IElementType elementType) {
    final PsiBuilder.Marker idMarker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.IDENTIFIER) {
      String id_text = myBuilder.getTokenText();
      myBuilder.advanceLexer();
      idMarker.done(elementType);
      return id_text;
    }
    else {
      myBuilder.error(IDENTIFIER_EXPECTED);
      idMarker.drop();
    }
    return null;
  }

  public boolean parseOptionalDottedName() {
    return parseDottedNameAsAware(false, true);
  }

  public boolean parseDottedName() {
    return parseDottedNameAsAware(false, false);
  }

  // true if identifier was parsed or skipped as optional, false on error
  protected boolean parseDottedNameAsAware(boolean expect_as, boolean optional) {
    if (myBuilder.getTokenType() != PyTokenTypes.IDENTIFIER) {
      if (optional) return true;
      myBuilder.error(IDENTIFIER_EXPECTED);
      return false;
    }
    PsiBuilder.Marker marker = myBuilder.mark();
    myBuilder.advanceLexer();
    marker.done(getReferenceType());
    boolean old_expect_AS_kwd = myExpectAsKeyword;
    myExpectAsKeyword = expect_as;
    while (myBuilder.getTokenType() == PyTokenTypes.DOT) {
      marker = marker.precede();
      myBuilder.advanceLexer();
      checkMatches(PyTokenTypes.IDENTIFIER, IDENTIFIER_EXPECTED);
      marker.done(getReferenceType());
    }
    myExpectAsKeyword = old_expect_AS_kwd;
    return true;
  }

  private void parseNameDefiningStatement(ParsingScope scope, final PyElementType elementType) {
    final PsiBuilder.Marker globalStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    while (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
      myBuilder.advanceLexer();
      parseIdentifier(PyElementTypes.TARGET_EXPRESSION);
    }
    checkEndOfStatement(scope);
    globalStatement.done(elementType);
  }

  private void parseExecStatement(ParsingScope inSuite) {
    assertCurrentToken(PyTokenTypes.EXEC_KEYWORD);
    final PsiBuilder.Marker execStatement = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression(true, false);
    if (myBuilder.getTokenType() == PyTokenTypes.IN_KEYWORD) {
      myBuilder.advanceLexer();
      getExpressionParser().parseSingleExpression(false);
      if (myBuilder.getTokenType() == PyTokenTypes.COMMA) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(false);
      }
    }
    checkEndOfStatement(inSuite);
    execStatement.done(PyElementTypes.EXEC_STATEMENT);
  }

  protected void parseIfStatement(PyElementType ifKeyword, PyElementType elifKeyword, PyElementType elseKeyword, PyElementType elementType,
                                  ParsingScope scope) {
    assertCurrentToken(ifKeyword);
    final PsiBuilder.Marker ifStatement = myBuilder.mark();
    final PsiBuilder.Marker ifPart = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression();
    parseColonAndSuite(scope);
    ifPart.done(PyElementTypes.IF_PART_IF);
    PsiBuilder.Marker elifPart = myBuilder.mark();
    while (myBuilder.getTokenType() == elifKeyword) {
      myBuilder.advanceLexer();
      getExpressionParser().parseExpression();
      parseColonAndSuite(scope);
      elifPart.done(PyElementTypes.IF_PART_ELIF);
      elifPart = myBuilder.mark();
    }
    elifPart.drop(); // we always kept an open extra elif
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == elseKeyword) {
      myBuilder.advanceLexer();
      parseColonAndSuite(scope);
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    ifStatement.done(elementType);
  }

  private boolean expectColon() {
    if (myBuilder.getTokenType() == PyTokenTypes.COLON) {
      myBuilder.advanceLexer();
      return true;
    }
    final PsiBuilder.Marker marker = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();
    }
    marker.error("Colon expected");
    return false;
  }

  private void parseForStatement(ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.FOR_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    parseForPart(scope);
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite(scope);
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    statement.done(PyElementTypes.FOR_STATEMENT);
  }

  protected void parseForPart(ParsingScope scope) {
    final PsiBuilder.Marker forPart = myBuilder.mark();
    myBuilder.advanceLexer();
    getExpressionParser().parseExpression(true, true);
    checkMatches(PyTokenTypes.IN_KEYWORD, "'in' expected");
    getExpressionParser().parseExpression();
    parseColonAndSuite(scope);
    forPart.done(PyElementTypes.FOR_PART);
  }

  private void parseWhileStatement(ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.WHILE_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    final PsiBuilder.Marker whilePart = myBuilder.mark();
    myBuilder.advanceLexer();
    if (!getExpressionParser().parseSingleExpression(false)) {
      myBuilder.error(EXPRESSION_EXPECTED);
    }
    parseColonAndSuite(scope);
    whilePart.done(PyElementTypes.WHILE_PART);
    final PsiBuilder.Marker elsePart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite(scope);
      elsePart.done(PyElementTypes.ELSE_PART);
    }
    else {
      elsePart.drop();
    }
    statement.done(PyElementTypes.WHILE_STATEMENT);
  }

  private void parseTryStatement(ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.TRY_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    final PsiBuilder.Marker tryPart = myBuilder.mark();
    myBuilder.advanceLexer();
    parseColonAndSuite(scope);
    tryPart.done(PyElementTypes.TRY_PART);
    boolean haveExceptClause = false;
    if (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
      haveExceptClause = true;
      while (myBuilder.getTokenType() == PyTokenTypes.EXCEPT_KEYWORD) {
        final PsiBuilder.Marker exceptBlock = myBuilder.mark();
        myBuilder.advanceLexer();
        if (myBuilder.getTokenType() != PyTokenTypes.COLON) {
          if (!getExpressionParser().parseSingleExpression(false)) {
            myBuilder.error(EXPRESSION_EXPECTED);
          }
          myExpectAsKeyword = true;
          if (myBuilder.getTokenType() == PyTokenTypes.COMMA || myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
            myBuilder.advanceLexer();
            if (!getExpressionParser().parseSingleExpression(true)) {
              myBuilder.error(EXPRESSION_EXPECTED);
            }
          }
        }
        parseColonAndSuite(scope);
        exceptBlock.done(PyElementTypes.EXCEPT_PART);
      }
      final PsiBuilder.Marker elsePart = myBuilder.mark();
      if (myBuilder.getTokenType() == PyTokenTypes.ELSE_KEYWORD) {
        myBuilder.advanceLexer();
        parseColonAndSuite(scope);
        elsePart.done(PyElementTypes.ELSE_PART);
      }
      else {
        elsePart.drop();
      }
    }
    final PsiBuilder.Marker finallyPart = myBuilder.mark();
    if (myBuilder.getTokenType() == PyTokenTypes.FINALLY_KEYWORD) {
      myBuilder.advanceLexer();
      parseColonAndSuite(scope);
      finallyPart.done(PyElementTypes.FINALLY_PART);
    }
    else {
      finallyPart.drop();
      if (!haveExceptClause) {
        myBuilder.error("'except' or 'finally' expected");
        // much better to have a statement of incorrectly determined type
        // than "TRY" and "COLON" tokens attached to nothing
      }
    }
    statement.done(PyElementTypes.TRY_EXCEPT_STATEMENT);
  }

  private void parseColonAndSuite(ParsingScope scope) {
    if (expectColon()) {
      parseSuite(scope);
    }
    else {
      final PsiBuilder.Marker mark = myBuilder.mark();
      mark.done(PyElementTypes.STATEMENT_LIST);
    }
  }

  private void parseWithStatement(ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.WITH_KEYWORD);
    final PsiBuilder.Marker statement = myBuilder.mark();
    myBuilder.advanceLexer();
    while (true) {
      PsiBuilder.Marker withItem = myBuilder.mark();
      getExpressionParser().parseExpression();
      myExpectAsKeyword = true;
      if (myBuilder.getTokenType() == PyTokenTypes.AS_KEYWORD) {
        myBuilder.advanceLexer();
        getExpressionParser().parseSingleExpression(true); // 'as' is followed by a target
      }
      withItem.done(PyElementTypes.WITH_ITEM);
      if (!matchToken(PyTokenTypes.COMMA)) {
        break;
      }
    }
    parseColonAndSuite(scope);
    statement.done(PyElementTypes.WITH_STATEMENT);
  }

  private void parseClassDeclaration(ParsingScope scope) {
    final PsiBuilder.Marker classMarker = myBuilder.mark();
    parseClassDeclaration(classMarker, scope);
  }

  public void parseClassDeclaration(PsiBuilder.Marker classMarker, ParsingScope scope) {
    assertCurrentToken(PyTokenTypes.CLASS_KEYWORD);
    myBuilder.advanceLexer();
    checkMatches(PyTokenTypes.IDENTIFIER, IDENTIFIER_EXPECTED);
    if (myBuilder.getTokenType() == PyTokenTypes.LPAR) {
      getExpressionParser().parseArgumentList();
    }
    else {
      final PsiBuilder.Marker inheritMarker = myBuilder.mark();
      inheritMarker.done(PyElementTypes.ARGUMENT_LIST);
    }
    parseColonAndSuite(scope.withClass(true));
    classMarker.done(PyElementTypes.CLASS_DECLARATION);
  }

  public void parseSuite(ParsingScope scope) {
    parseSuite(null, null, scope);
  }

  public void parseSuite(@Nullable PsiBuilder.Marker endMarker, @Nullable IElementType elType, ParsingScope scope) {
    if (myBuilder.getTokenType() == PyTokenTypes.STATEMENT_BREAK) {
      myBuilder.advanceLexer();

      final PsiBuilder.Marker marker = myBuilder.mark();
      if (myBuilder.getTokenType() != PyTokenTypes.INDENT) {
        myBuilder.error("Indent expected");
      }
      else {
        myBuilder.advanceLexer();
        if (myBuilder.eof()) {
          myBuilder.error("Indented block expected");
        }
        else {
          while (!myBuilder.eof() && myBuilder.getTokenType() != PyTokenTypes.DEDENT) {
            parseStatement(scope);
          }
        }
      }

      marker.done(PyElementTypes.STATEMENT_LIST);
      marker.setCustomEdgeTokenBinders(null, FollowingCommentBinder.INSTANCE);
      if (endMarker != null) {
        endMarker.done(elType);
      }
      if (!myBuilder.eof()) {
        checkMatches(PyTokenTypes.DEDENT, "Dedent expected");
      }
      // NOTE: the following line advances the PsiBuilder lexer and thus
      // ensures that the whitespace following the statement list is included
      // in the block containing the statement list
      myBuilder.getTokenType();
    }
    else {
      final PsiBuilder.Marker marker = myBuilder.mark();
      if (myBuilder.eof()) {
        myBuilder.error("Statement expected");
      }
      else {
        parseSimpleStatement(scope.withSuite(true));
        while (matchToken(PyTokenTypes.SEMICOLON)) {
          if (matchToken(PyTokenTypes.STATEMENT_BREAK)) {
            break;
          }
          parseSimpleStatement(scope.withSuite(true));
        }
      }
      marker.done(PyElementTypes.STATEMENT_LIST);
      if (endMarker != null) {
        endMarker.done(elType);
      }
    }
  }

  public IElementType filter(final IElementType source, final int start, final int end, final CharSequence text) {
    if (
      (myExpectAsKeyword || myContext.getLanguageLevel().hasWithStatement()) &&
      source == PyTokenTypes.IDENTIFIER && isWordAtPosition(text, start, end, TOK_AS)
      ) {
      return PyTokenTypes.AS_KEYWORD;
    }
    else if ( // filter
      (myFutureImportPhase == Phase.FROM) &&
      source == PyTokenTypes.IDENTIFIER &&
      isWordAtPosition(text, start, end, TOK_FUTURE_IMPORT)
      ) {
      myFutureImportPhase = Phase.FUTURE;
      return source;
    }
    else if (
      hasWithStatement() &&
      source == PyTokenTypes.IDENTIFIER &&
      isWordAtPosition(text, start, end, TOK_WITH)
      ) {
      return PyTokenTypes.WITH_KEYWORD;
    }
    else if (hasPrintStatement() && source == PyTokenTypes.IDENTIFIER &&
             isWordAtPosition(text, start, end, TOK_PRINT)) {
      return PyTokenTypes.PRINT_KEYWORD;
    }
    else if (myContext.getLanguageLevel().isPy3K() && source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_NONE)) {
        return PyTokenTypes.NONE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_TRUE)) {
        return PyTokenTypes.TRUE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_FALSE)) {
        return PyTokenTypes.FALSE_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_DEBUG)) {
        return PyTokenTypes.DEBUG_KEYWORD;
      }
      if (isWordAtPosition(text, start, end, TOK_NONLOCAL)) {
        return PyTokenTypes.NONLOCAL_KEYWORD;
      }
    }
    else if (!myContext.getLanguageLevel().isPy3K() && source == PyTokenTypes.IDENTIFIER) {
      if (isWordAtPosition(text, start, end, TOK_EXEC)) {
        return PyTokenTypes.EXEC_KEYWORD;
      }
    }
    return source;
  }

  protected TokenSet getEndOfStatementsTokens() {
    return PyTokenTypes.END_OF_STATEMENT;
  }

  private static boolean isWordAtPosition(CharSequence text, int start, int end, final String tokenText) {
    return CharArrayUtil.regionMatches(text, start, end, tokenText) && end - start == tokenText.length();
  }

  private boolean hasWithStatement() {
    return myContext.getLanguageLevel().hasWithStatement() || myFutureFlags.contains(FUTURE.WITH_STATEMENT);
  }
}
