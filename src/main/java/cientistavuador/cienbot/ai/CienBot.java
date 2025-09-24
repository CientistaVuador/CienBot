package cientistavuador.cienbot.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Cien
 */
public class CienBot {

    private final Random random = new Random();

    private final int maxContextSize;

    private final Map<Token, Token> tokenMap = new ConcurrentHashMap<>();
    private final Map<Context, Context> contextMap = new ConcurrentHashMap<>();

    public CienBot(int maxContextSize) {
        this.maxContextSize = maxContextSize;
        if (this.maxContextSize <= 0) {
            throw new IllegalArgumentException("max context size must be larger than zero");
        }
    }

    public int getMaxContextSize() {
        return maxContextSize;
    }

    public Token[] getTokens() {
        return this.tokenMap.keySet().toArray(Token[]::new);
    }

    public Context[] getContexts() {
        return this.contextMap.keySet().toArray(Context[]::new);
    }

    private Token getToken(String text, boolean createNew) {
        Token t = new Token(text);
        Token o = this.tokenMap.get(t);
        if (o != null) {
            return o;
        }
        if (!createNew) {
            return null;
        }
        this.tokenMap.put(t, t);
        return t;
    }

    private boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    private List<Token> tokenize(String text, boolean createNew) {
        List<Token> tokens = new ArrayList<>();

        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isSpace(c)) {
                String tokenText = b.toString();
                b.setLength(0);
                if (!tokenText.isEmpty()) {
                    tokens.add(getToken(tokenText, createNew));
                }
                continue;
            }
            b.append(c);
        }
        String tokenText = b.toString();
        if (!tokenText.isEmpty()) {
            tokens.add(getToken(tokenText, createNew));
        }
        
        if (!tokens.isEmpty() && createNew) {
            tokens.get(0).incrementStartCount();
        }

        return tokens;
    }

    private Context getContext(List<Token> tokens, boolean createNew) {
        Context c = new Context(tokens.toArray(Token[]::new));
        Context o = this.contextMap.get(c);
        if (o != null) {
            return o;
        }
        if (!createNew) {
            return null;
        }
        this.contextMap.put(c, c);
        return c;
    }

    private void contextualize(List<Token> tokens) {
        List<Token> contextTokens = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token nextToken = null;
            if ((i + 1) < tokens.size()) {
                nextToken = tokens.get(i + 1);
            }

            for (int j = 0; j < this.maxContextSize; j++) {
                int tokenIndex = i - j;
                if (tokenIndex < 0) {
                    break;
                }
                
                contextTokens.add(0, tokens.get(tokenIndex));

                getContext(contextTokens, true).incrementNextTokenCount(nextToken);
            }
            contextTokens.clear();
        }
    }

    public void teach(String message) {
        contextualize(tokenize(message, true));
    }

    private Token getRandomStartToken() {
        Token[] tokens = getTokens();

        int sum = 0;
        for (Token t : tokens) {
            sum += t.getStartCount();
        }

        if (sum == 0) {
            return null;
        }

        int randomValue = this.random.nextInt(sum);

        int offset = 0;
        for (Token t : tokens) {
            int count = t.getStartCount();
            if (randomValue >= offset && randomValue < (offset + count)) {
                return t;
            }
            offset += count;
        }

        return null;
    }

    private Token getNextToken(List<Token> currentMessage) {
        for (int len = this.maxContextSize; len > 0; len--) {
            int srcPos = currentMessage.size() - len;
            if (srcPos < 0) {
                continue;
            }
            Context context = getContext(currentMessage.subList(srcPos, srcPos + len), false);
            if (context != null) {
                return context.getRandomNextToken();
            }
        }

        return null;
    }

    private List<Token> generateTokens(List<Token> append, int maxSize) {
        List<Token> tokenList = new ArrayList<>();
        if (append != null && !append.isEmpty()) {
            tokenList.addAll(append);
        } else {
            Token startToken = getRandomStartToken();
            if (startToken == null) {
                return new ArrayList<>();
            }
            tokenList.add(startToken);
        }
        
        for (int i = 1; i < maxSize; i++) {
            Token next = getNextToken(tokenList);
            if (next == null) {
                break;
            }
            tokenList.add(next);
        }

        return tokenList;
    }

    private String compileTokens(List<Token> tokens) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            b.append(tokens.get(i).getText());
            if (i != tokens.size() - 1) {
                b.append(' ');
            }
        }
        return b.toString();
    }

    public String generate(String messageToComplete, int maxTokens) {
        List<Token> completeTokens = null;
        if (messageToComplete != null && !messageToComplete.isEmpty()) {
            completeTokens = tokenize(messageToComplete, false);
            int cutoff = 0;
            for (int i = completeTokens.size() - 1; i >= 0; i--) {
                if (completeTokens.get(i) == null) {
                    cutoff = i + 1;
                    break;
                }
                cutoff = i;
            }
            completeTokens = completeTokens.subList(cutoff, completeTokens.size());
        }
        return compileTokens(generateTokens(completeTokens, maxTokens));
    }

    public String generate(int maxTokens) {
        return generate(null, maxTokens);
    }
}
