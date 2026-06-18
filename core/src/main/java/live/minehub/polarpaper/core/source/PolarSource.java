package live.minehub.polarpaper.core.source;

public interface PolarSource {
    byte[] readBytes() throws Exception;

    void saveBytes(byte[] save) throws Exception;

    default void delete() throws Exception {
        throw new UnsupportedOperationException("This PolarSource does not support deletion");
    }
}
