package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;

import java.util.ArrayList;
import java.util.Arrays;
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

    // declaration → classDecl
    //             | funDecl
    //             | varDecl
    //             | statement ;
    //
    // funDecl  → "fun" function ;
    //
    private Stmt declaration() {
        try {
            if (match(CLASS)) {
                return classDeclaration();
            }
            if (match(FUN)) {
                return function("function");
            }
            if (match(VAR)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // classDecl   → "class" IDENTIFIER "{" function* "}" ;
    //
    private Stmt classDeclaration() {
        Token name = consume(IDENTIFIER, "Expect class name.");
        consume(LEFT_BRACE, "Expect '{' before class body.");
    
        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
          methods.add(function("method"));
        }
    
        consume(RIGHT_BRACE, "Expect '}' after class body.");
    
        return new Stmt.Class(name, methods);
    }

    // statement → exprStmt
    //           | forStmt
    //           | ifStmt
    //           | printStmt
    //           | returnStmt
    //           | whileStmt
    //           | block ;
    // 
    private Stmt statement() {
        if (match(FOR)) {
            return forStatement();
        }
        if (match(IF)) {
            return ifStatement();
        }
        if (match(PRINT)) {
            return printStatement();
        }
        if (match(RETURN)) {
            return returnStatement();
        }
        if (match(WHILE)) {
            return whileStatement();
        }
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        return expressionStatement();
    }

    // forStmt   → "for" "(" ( varDecl | exprStmt | ";" )
    //                   expression? ";"
    //                   expression? ")" statement ;
    // 
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
    
        // Handle initilaiser...
        Stmt initializer;
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        // Handle conditional...
        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition.");

        // Handle post step...
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        // Handle statement via 'de-sugaring'...
        Stmt body = statement();

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(
                body,
                new Stmt.Expression(increment)));
        }

        if (condition == null) {
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }

        return body;
    }

    // ifStmt    → "if" "(" expression ")" statement ( "else" statement )? ;
    //
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition."); 
    
        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
    
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    // block     → "{" declaration* "}" ;
    //
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
    
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
          statements.add(declaration());
        }
    
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // printStmt → "print" expression ";" ;
    //
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    // returnStmt → "return" expression? ";" ;
    // 
    private Stmt returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
    
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
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


    // whileStmt → "while" "(" expression ")" statement ;
    //
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();
    
        return new Stmt.While(condition, body);
    }


    // exprStmt  → expression ";" ;
    //
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }


    // function → IDENTIFIER "(" parameters? ")" block ;
    // 
    private Stmt.Function function(String kind) {
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");

        // Parse Parameters List
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 8) {
                    error(peek(), "Cannot have more than 8 parameters.");
                }
        
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");

        // Parse Function Body
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }


    // Binary Expression Operations
    //

    // expression → assignment ;
    //
    private Expr expression() {
        return assignment();
    }
    

    // assignment → identifier "=" assignment
    //            | logic_or ;
    private Expr assignment() {
        Expr expr = or();
    
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


    // logic_or   → logic_and ( "or" logic_and )* ;
    //
    private Expr or() {
        Expr expr = and();
    
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
    
        return expr;
    }


    // logic_and  → equality ( "and" equality )* ;
    //
    private Expr and() {
        Expr expr = equality();
    
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
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
    //   | call ;
    //
    private Expr unary() {
        if (match(BANG, MINUS)) {
          Token operator = previous();
          Expr right = unary();
          return new Expr.Unary(operator, right);
        }
    
        return call();
    }


    // call  → primary ( "(" arguments? ")" )* ;
    //
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 8) {
                    error(peek(), "Cannot have more than 8 arguments.");
                }
                arguments.add(expression());
            } while (match(COMMA));
        }
    
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
    
        return new Expr.Call(callee, paren, arguments);
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