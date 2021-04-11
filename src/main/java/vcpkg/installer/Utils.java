package vcpkg.installer;

public class Utils {
    public interface TetraConsumer<A, B, C, D> {
        void call(A a, B b, C c, D d);
    }

    public interface TriConsumer<A, B, C> {
        void call(A a, B b, C c);
    }
}
