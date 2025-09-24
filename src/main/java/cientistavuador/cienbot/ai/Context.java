package cientistavuador.cienbot.ai;

import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Cien
 */
public class Context {

    private final Random random = new Random();
    private final Token[] tokens;
    private final Map<Token, Integer> nextTokens = new ConcurrentHashMap<>();
    private int nullTokenCount = 0;

    public Context(Token[] tokens) {
        this.tokens = tokens.clone();
        for (int i = 0; i < this.tokens.length; i++) {
            if (this.tokens[i] == null) {
                throw new NullPointerException("token is null at index " + i);
            }
        }
    }

    public Token[] getTokens() {
        return tokens.clone();
    }

    public Token[] getNextTokens() {
        return this.nextTokens.keySet().toArray(Token[]::new);
    }

    public int getNextTokenCount(Token t) {
        if (t == null) {
            return this.nullTokenCount;
        }

        Integer count = this.nextTokens.get(t);
        if (count == null) {
            count = 0;
        }
        return count;
    }

    public void incrementNextTokenCount(Token t) {
        if (t == null) {
            this.nullTokenCount++;
            return;
        }

        Integer count = this.nextTokens.get(t);
        if (count == null) {
            count = 0;
        }
        this.nextTokens.put(t, count + 1);
    }
    
    public Token getRandomNextToken() {
        int sum = 0;

        sum += this.nullTokenCount;
        for (Integer c : this.nextTokens.values()) {
            sum += c;
        }

        if (sum <= 0) {
            return null;
        }

        int randomValue = this.random.nextInt(sum);
        
        int offset = 0;
        if (randomValue >= offset && randomValue < (offset + this.nullTokenCount)) {
            return null;
        }
        offset += this.nullTokenCount;
        for (Map.Entry<Token, Integer> e : this.nextTokens.entrySet()) {
            int count = e.getValue();
            if (randomValue >= offset && randomValue < (offset + count)) {
                return e.getKey();
            }
            offset += count;
        }

        return null;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + Arrays.deepHashCode(this.tokens);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Context other = (Context) obj;
        return Arrays.deepEquals(this.tokens, other.tokens);
    }

}
