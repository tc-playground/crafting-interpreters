package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.BANG;
import static com.craftinginterpreters.lox.TokenType.BANG_EQUAL;
import static com.craftinginterpreters.lox.TokenType.EOF;
import static com.craftinginterpreters.lox.TokenType.EQUAL;
import static com.craftinginterpreters.lox.TokenType.EQUAL_EQUAL;
import static com.craftinginterpreters.lox.TokenType.FALSE;
import static com.craftinginterpreters.lox.TokenType.GREATER;
import static com.craftinginterpreters.lox.TokenType.GREATER_EQUAL;
import static com.craftinginterpreters.lox.TokenType.IDENTIFIER;
import static com.craftinginterpreters.lox.TokenType.LEFT_PAREN;
import static com.craftinginterpreters.lox.TokenType.LESS;
import static com.craftinginterpreters.lox.TokenType.LESS_EQUAL;
import static com.craftinginterpreters.lox.TokenType.MINUS;
import static com.craftinginterpreters.lox.TokenType.NIL;
import static com.craftinginterpreters.lox.TokenType.NUMBER;
import static com.craftinginterpreters.lox.TokenType.PLUS;
import static com.craftinginterpreters.lox.TokenType.PRINT;
import static com.craftinginterpreters.lox.TokenType.RIGHT_PAREN;
import static com.craftinginterpreters.lox.TokenType.SEMICOLON;
import static com.craftinginterpreters.lox.TokenType.SLASH;
import static com.craftinginterpreters.lox.TokenType.STAR;
import static com.craftinginterpreters.lox.TokenType.STRING;
import static com.craftinginterpreters.lox.TokenType.TRUE;
import static com.craftinginterpreters.lox.TokenType.VAR;

import java.util.ArrayList;
import java.util.List;

class Parser {

    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    
    // program   → declaration* EOF ;
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }


    // Production Rules *******************************************************

    // declaration → varDecl
    //             | statement ;
    private Stmt declaration() {
        try {
            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // statement → exprStmt
    //           | printStmt ;
    //
    private Stmt statement() {
        if (match(PRINT)) {
            return printStatement();
        }
        return expressionStatement();
    }

    // printStmt → "print" expression ";" ;
    //
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // varDecl → "var" IDENTIFIER ( "=" expression )? ";" ;
    //
    private Stmt varDeclaration() {
        Token name = consume(IDENTIFIER, "Expect variable name.");

        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        consume(SEMICOLON, "Expect ';' after variable declaration.");
       return new Stmt.Var(name, initializer);
    }


    // exprStmt  → expression ";" ;
    //
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }


    // Binary Expression Operations
    //

    // expression → assignment ;
    //
    private Expr expression() {
        return assignment();
    }
    
    // assignment → identifier "=" assignment
    //            | equality ;
    //
    private Expr assignment() {
        Expr expr = equality();
    
        if (match(EQUAL)) {
            Token equals = previous();
            // Recursively call assignment to evaluate the value.
            // This is possible as assignment is rught associative.
            Expr value = assignment();
        
            // Trick: Check the expression is a valid assignemnt target.
            // This trick works because it turns out that every valid 
            // assignment target happens to also be valid syntax as a 
            // normal expression.
            if (expr instanceof Expr.Variable) {
                // Evaluate the target
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }
        
            error(equals, "Invalid assignment target."); 
        }
    
        return expr;
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    //
    private Expr equality() {
        Expr expr = comparison();
    
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
    
        return expr;
    }


    // comparison → addition ( ( ">" | ">=" | "<" | "<=" ) addition )* ;
    //
    private Expr comparison() {
        Expr expr = addition();
    
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }
    
        return expr;
    }

    
    // addition → multiplication ( ( "-" | "+" ) multiplication )* ;
    //
    private Expr addition() {
        Expr expr = multiplication();

        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    
    // multiplication → unary ( ( "/" | "*" ) unary )* ;
    //
    private Expr multiplication() {
        Expr expr = unary();

        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }


    // Unary Expression Operations
    //

    // unary → ( "!" | "-" ) unary
    //   | primary ;
    //
    private Expr unary() {
        if (match(BANG, MINUS)) {
          Token operator = previous();
          Expr right = unary();
          return new Expr.Unary(operator, right);
        }
    
        return primary();
    }


    // Primary Expressions
    //
    // * The highest level of precedence, 'primary expressions'.
    // * These are mostly terminals.


    // primary → NUMBER | STRING | "false" | "true" | "nil"
    //     | "(" expression ")" ;
    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);
    
        if (match(NUMBER, STRING)) {
             return new Expr.Literal(previous().literal);
        }
    
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    // Parser Functions *******************************************************

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
    
        return false;
    }

    private boolean check(TokenType tokenType) {
        if (isAtEnd()) return false;
        return peek().type == tokenType;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }
    
    private Token peek() {
        return tokens.get(current);
    }
    
    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }

        throw error(peek(), message);
    }

    // Error Handling

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();
    
        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) {
                return;
            }
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                return;
            }
        
            advance();
        }
    }

}