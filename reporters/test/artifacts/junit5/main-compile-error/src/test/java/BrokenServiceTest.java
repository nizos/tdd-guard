import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BrokenServiceTest {
    @Test
    void testCompute() {
        BrokenService service = new BrokenService();
        assertEquals(42, service.compute());
    }
}
