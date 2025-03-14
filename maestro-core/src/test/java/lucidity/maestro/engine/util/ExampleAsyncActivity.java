package lucidity.maestro.engine.util;

import lucidity.maestro.engine.api.activity.ActivityInterface;

@ActivityInterface
public interface ExampleAsyncActivity {

    String workFor1SecondAndEcho(String valToEcho);

    String workFor2SecondsAndEcho(String valToEcho);
}
