package lucidity.maestro.engine.util;

public class ExampleAsyncActivityImpl implements ExampleAsyncActivity {
    @Override
    public String workFor1SecondAndEcho(String valToEcho) {
        sleep(1000);
        return valToEcho;
    }

    @Override
    public String workFor2SecondsAndEcho(String valToEcho) {
        sleep(2000);
        return valToEcho;
    }

    private void sleep(long durationInMillis) {
        try {
            Thread.sleep(durationInMillis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
