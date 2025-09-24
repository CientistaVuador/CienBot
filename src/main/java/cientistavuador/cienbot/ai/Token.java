package cientistavuador.cienbot.ai;

import java.util.Objects;

/**
 *
 * @author Cien
 */
public class Token {
    
    private final String text;
    
    private int startCount = 0;

    public Token(String text) {
        Objects.requireNonNull(text, "text is null");
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public int getStartCount() {
        return startCount;
    }

    public void incrementStartCount() {
        this.startCount++;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.text);
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
        final Token other = (Token) obj;
        return Objects.equals(this.text, other.text);
    }
    
}
