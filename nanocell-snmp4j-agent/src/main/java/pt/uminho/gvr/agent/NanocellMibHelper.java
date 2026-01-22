package pt.uminho.gvr.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snmp4j.agent.mo.MOMutableTableModel;
import org.snmp4j.agent.mo.MOTableModel;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import org.snmp4j.smi.Counter32;
import org.snmp4j.smi.Gauge32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TimeTicks;
import org.snmp4j.smi.UnsignedInteger32;
import org.snmp4j.smi.Variable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;

/**
 * Thin helper around {@link NanocellMib}. It only:
 * <ul>
 *   <li>Loads the initial JSON config into the MIB when constructed.</li>
 *   <li>Exposes simple, synchronized accessors and a move operation without surfacing OIDs.</li>
 * </ul>
 */
public class NanocellMibHelper {

  private static final LogAdapter LOGGER = LogFactory.getLogger(NanocellMibHelper.class);
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final String CONFIG_RESOURCE = "/config/nanocell_mib_config.json";

  public static final class CellSnapshot {
    public final int cellId;
    public final String cellName;
    public final long cellMaxUsers;
    public final long cellCurrentUsers;
    public final long cellHandoversInTotal;
    public final long cellHandoversOutTotal;

    private CellSnapshot(int cellId,
                         String cellName,
                         long cellMaxUsers,
                         long cellCurrentUsers,
                         long cellHandoversInTotal,
                         long cellHandoversOutTotal) {
      this.cellId = cellId;
      this.cellName = cellName;
      this.cellMaxUsers = cellMaxUsers;
      this.cellCurrentUsers = cellCurrentUsers;
      this.cellHandoversInTotal = cellHandoversInTotal;
      this.cellHandoversOutTotal = cellHandoversOutTotal;
    }
  }

  public static final class NeighSnapshot {
    public final int sourceCellId;
    public final int neighborCellId;
    public final long weight;

    private NeighSnapshot(int sourceCellId, int neighborCellId, long weight) {
      this.sourceCellId = sourceCellId;
      this.neighborCellId = neighborCellId;
      this.weight = weight;
    }
  }

  private record Config(long totalUEs,
                        long mobilityIntervalTicks,
                        List<CellConfig> cells,
                        List<NeighborConfig> neighbors) {
  }

  private record CellConfig(int cellId,
                            String cellName,
                            long cellMaxUsers,
                            long cellCurrentUsers,
                            Long cellHandoversInTotal,
                            Long cellHandoversOutTotal) {
    long inTotal() {
      if (cellHandoversInTotal == null)
        return 0L;
      else
        return cellHandoversInTotal;
    }

    long outTotal() {
      if (cellHandoversOutTotal == null)
        return 0L;
      else
        return cellHandoversOutTotal;
    }
  }

  private record NeighborConfig(int sourceCellId, 
                                int neighborCellId, 
                                long neighborWeight) {
  }

  private final NanocellMib nanocellMib;

  public NanocellMibHelper(NanocellMib nanocellMib) {
    this.nanocellMib = nanocellMib;
    loadInitialConfig();
  }

  private void loadInitialConfig() {
    try (InputStream in = NanocellMibHelper.class.getResourceAsStream(CONFIG_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Cannot find initial config " + CONFIG_RESOURCE);
      }
      Config config = MAPPER.readValue(in, Config.class);
      seedFromConfig(config);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load initial config", e);
    }
  }

  private void seedFromConfig(Config config) {
    nanocellMib.getTotalUEs().setValue(new UnsignedInteger32(config.totalUEs()));
    nanocellMib.getMobilityInterval().setValue(new TimeTicks(config.mobilityIntervalTicks()));
    seedCellTable(config.cells());
    seedNeighborTable(config.neighbors());
    LOGGER.info("Initial MIB seeded from " + CONFIG_RESOURCE);
  }

  private void seedCellTable(List<CellConfig> cells) {
    @SuppressWarnings("unchecked")
    MOMutableTableModel<NanocellMib.CellEntryRow> model =
        (MOMutableTableModel<NanocellMib.CellEntryRow>) nanocellMib.getCellEntry().getModel();
    model.clear();
    if (cells == null) {
      return;
    }
    for (CellConfig cell : cells) {
      Variable[] values = new Variable[]{
          new OctetString(cell.cellName()),
          new UnsignedInteger32(cell.cellMaxUsers()),
          new Gauge32(cell.cellCurrentUsers()),
          new Counter32(cell.inTotal()),
          new Counter32(cell.outTotal())
      };
      NanocellMib.CellEntryRow row =
          nanocellMib.new CellEntryRow(new OID(new int[]{cell.cellId()}), values);
      model.addRow(row);
    }
  }

  private void seedNeighborTable(List<NeighborConfig> neighbors) {
    @SuppressWarnings("unchecked")
    MOMutableTableModel<NanocellMib.CellNeighborEntryRow> model =
        (MOMutableTableModel<NanocellMib.CellNeighborEntryRow>) nanocellMib.getCellNeighborEntry().getModel();
    model.clear();
    if (neighbors == null) {
      return;
    }
    for (NeighborConfig neighbor : neighbors) {
      Variable[] values = new Variable[]{new UnsignedInteger32(neighbor.neighborWeight())};
      NanocellMib.CellNeighborEntryRow row =
          nanocellMib.new CellNeighborEntryRow(
              new OID(new int[]{neighbor.sourceCellId(), neighbor.neighborCellId()}),
              values);
      model.addRow(row);
    }
  }

  public synchronized List<CellSnapshot> getCells() {
    List<CellSnapshot> cells = new ArrayList<>();
    @SuppressWarnings("unchecked")
    MOTableModel<NanocellMib.CellEntryRow> model =
        (MOTableModel<NanocellMib.CellEntryRow>) nanocellMib.getCellEntry().getModel();
    Iterator<NanocellMib.CellEntryRow> it = model.iterator();
    while (it.hasNext()) {
      NanocellMib.CellEntryRow row = it.next();
      int cellId = row.getIndex().get(0);
      cells.add(new CellSnapshot(
          cellId,
          row.getCellName().toString(),
          row.getCellMaxUsers().getValue(),
          row.getCellCurrentUsers().getValue(),
          row.getCellHandoversInTotal().getValue(),
          row.getCellHandoversOutTotal().getValue()
      ));
    }
    return cells;
  }

  public synchronized CellSnapshot getCellById(int cellId) {
    @SuppressWarnings("unchecked")
    MOTableModel<NanocellMib.CellEntryRow> model =
        (MOTableModel<NanocellMib.CellEntryRow>) nanocellMib.getCellEntry().getModel();
    NanocellMib.CellEntryRow row = model.getRow(new OID(new int[]{cellId}));
    if (row == null) {
      return null;
    }
    return new CellSnapshot(
        cellId,
        row.getCellName().toString(),
        row.getCellMaxUsers().getValue(),
        row.getCellCurrentUsers().getValue(),
        row.getCellHandoversInTotal().getValue(),
        row.getCellHandoversOutTotal().getValue()
    );
  }

  public synchronized List<NeighSnapshot> getNeighs() {
    List<NeighSnapshot> neighs = new ArrayList<>();
    @SuppressWarnings("unchecked")
    MOTableModel<NanocellMib.CellNeighborEntryRow> model =
        (MOTableModel<NanocellMib.CellNeighborEntryRow>) nanocellMib.getCellNeighborEntry().getModel();
    Iterator<NanocellMib.CellNeighborEntryRow> it = model.iterator();
    while (it.hasNext()) {
      NanocellMib.CellNeighborEntryRow row = it.next();
      OID idx = row.getIndex();
      neighs.add(new NeighSnapshot(idx.get(0), idx.get(1), row.getNeighborWeight().getValue()));
    }
    return neighs;
  }

  public synchronized NeighSnapshot getNeighById(int sourceCellId, int neighborCellId) {
    @SuppressWarnings("unchecked")
    MOTableModel<NanocellMib.CellNeighborEntryRow> model =
        (MOTableModel<NanocellMib.CellNeighborEntryRow>) nanocellMib.getCellNeighborEntry().getModel();
    NanocellMib.CellNeighborEntryRow row =
        model.getRow(new OID(new int[]{sourceCellId, neighborCellId}));
    if (row == null) {
      return null;
    }
    return new NeighSnapshot(sourceCellId, neighborCellId, row.getNeighborWeight().getValue());
  }

  public synchronized int getTotalUEs() {
    return nanocellMib.getTotalUEs().getValue().toInt();
  }

  public synchronized long getMobilityIntervalTicks() {
    Variable v = nanocellMib.getMobilityInterval().getValue();
    if (v instanceof TimeTicks ticks) {
      return ticks.toLong();
    }
    return 0L;
  }

  /**
   * Move up to {@code count} UEs from source to destination, respecting availability and capacity.
   */
  public synchronized void moveUEs(int sourceCellId, int destinationCellId, int count) {
    //LOGGER.info("calling NanocellMibHelper:moveUEs");
    if (count <= 0 || sourceCellId == destinationCellId) {
      LOGGER.info("bad call");
      return;
    }
    @SuppressWarnings("unchecked")
    MOMutableTableModel<NanocellMib.CellEntryRow> model =
        (MOMutableTableModel<NanocellMib.CellEntryRow>) nanocellMib.getCellEntry().getModel();

    NanocellMib.CellEntryRow src = model.getRow(new OID(new int[]{sourceCellId}));
    NanocellMib.CellEntryRow dst = model.getRow(new OID(new int[]{destinationCellId}));
    if (src == null || dst == null) {
      LOGGER.warn("Cannot move UEs, missing cells src=" + sourceCellId + " dst=" + destinationCellId);
      return;
    }

    long srcUsers = src.getCellCurrentUsers().getValue();
    long dstUsers = dst.getCellCurrentUsers().getValue();
    long dstCap = dst.getCellMaxUsers().getValue();

    long capacityLeft = Math.max(0, dstCap - dstUsers);
    if (capacityLeft < count) {
      LOGGER.error("Can't move, cell " +destinationCellId+ " is full");
      return;
    }

    src.setCellCurrentUsers(new Gauge32(srcUsers - count));
    src.setCellHandoversOutTotal(new Counter32(src.getCellHandoversOutTotal().getValue() + count));
    dst.setCellCurrentUsers(new Gauge32(dstUsers + count));
    dst.setCellHandoversInTotal(new Counter32(dst.getCellHandoversInTotal().getValue() + count));

    LOGGER.info("moved " + count + " UEs from cell: " + sourceCellId + " to cell: " + destinationCellId);
  }
}
