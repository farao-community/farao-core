package com.farao_community.farao.data.crac_impl.remedial_action.range_action;

import com.farao_community.farao.data.crac_api.NetworkElement;
import com.farao_community.farao.data.crac_api.Range;
import com.powsybl.iidm.network.Network;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;

/**
 * @author Alexandre Montigny {@literal <alexandre.montigny at rte-france.com>}
 */
public class RedispatchingTest extends AbstractRangeActionTest {
    private static final double DOUBLE_TOLERANCE = 0.01;

    private Redispatching redispatching;
    private NetworkElement generator;
    private double minPower = 0;
    private double maxPower = 200;
    private double targetPower = 100;
    private double startupCost = 1;
    private double marginalCost = 2;

    @Before
    public void setUp() {
        generator = Mockito.mock(NetworkElement.class);
        redispatching = new Redispatching(
                "rd_id",
                "rd_name",
                "rd_operator",
                createUsageRules(),
                createRanges(),
                minPower,
                maxPower,
                targetPower,
                startupCost,
                marginalCost,
                generator
        );
    }

    @Test
    public void basicTests() {
        redispatching.setMinimumPower(redispatching.getMinimumPower() + 1);
        redispatching.setMaximumPower(redispatching.getMaximumPower() + 1);
        redispatching.setTargetPower(redispatching.getTargetPower() + 1);
        redispatching.setStartupCost(redispatching.getStartupCost() + 1);
        redispatching.setMarginalCost(redispatching.getMarginalCost() + 1);
        assertEquals(minPower + 1, redispatching.getMinimumPower(), DOUBLE_TOLERANCE);
        assertEquals(maxPower + 1, redispatching.getMaximumPower(), DOUBLE_TOLERANCE);
        assertEquals(targetPower + 1, redispatching.getTargetPower(), DOUBLE_TOLERANCE);
        assertEquals(startupCost + 1, redispatching.getStartupCost(), DOUBLE_TOLERANCE);
        assertEquals(marginalCost + 1, redispatching.getMarginalCost(), DOUBLE_TOLERANCE);
    }

    @Test
    public void getMinAndMaxValueWithRange() {
        Range mockedRange = Mockito.mock(Range.class);
        Network mockedNetwork = Mockito.mock(Network.class);
        assertEquals(0, redispatching.getMinValueWithRange(mockedNetwork, mockedRange, 5), 0);
        assertEquals(0, redispatching.getMaxValueWithRange(mockedNetwork, mockedRange, 5), 0);
    }

    @Test
    public void equals() {
        assertEquals(redispatching, redispatching);
        NetworkElement networkElement = Mockito.mock(NetworkElement.class);
        Redispatching anotherRedispatching = new Redispatching("otherid", networkElement, 3.);
        assertNotEquals(redispatching, anotherRedispatching);
    }

    @Test
    public void alternativeConstructor() {
        Redispatching redispatching = new Redispatching("id", "name", "operator", 10, new NetworkElement("neID"));
        assertEquals("id", redispatching.getId());
    }
}