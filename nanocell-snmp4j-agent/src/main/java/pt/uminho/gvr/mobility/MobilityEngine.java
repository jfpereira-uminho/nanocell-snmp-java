package pt.uminho.gvr.mobility;

import org.snmp4j.log.LogAdapter;
import org.snmp4j.log.LogFactory;
import pt.uminho.gvr.agent.NanocellMibHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically moves a fraction of UEs from each cell to its neighbors using MIB weights.
 */
public class MobilityEngine implements Runnable {

  private static final LogAdapter LOGGER = LogFactory.getLogger(MobilityEngine.class);
  private static final double MOVE_RATIO_MAX = 30.0; // %
  private static final double MOVE_RATIO_MIN = 5.0; // %

  private final NanocellMibHelper mibHelper;
  private volatile boolean running = true;

  public MobilityEngine(NanocellMibHelper mibHelper) {
    this.mibHelper = mibHelper;
  }

  public void stop() {
    running = false;
  }

  @Override
  public void run() {
    LOGGER.debug("Mobility engine started ");
    while (running) {
      try {
        List<AuxCell> cellsList = buildStructure();
        if (!cellsList.isEmpty()) {
          cellsList.sort(Comparator
                    .comparingLong(AuxCell::capDelta)
                    .thenComparing((AuxCell c) -> c.cellCurrentUsers, Comparator.reverseOrder()));
          performMobilityStep(cellsList);
        }
      } catch (Exception e) {
        LOGGER.error("Mobility step failed", e);
      }

      long sleepMs = Math.max(0L, mibHelper.getMobilityIntervalTicks() * 10L);
      try {
        Thread.sleep(sleepMs);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    LOGGER.info("Mobility engine stopped");
  }

  private List<AuxCell> buildStructure() throws Exception {
    List<AuxCell> aux = new ArrayList<>();
    List<NanocellMibHelper.CellSnapshot> cellSnapshots = mibHelper.getCells();
    List<NanocellMibHelper.NeighSnapshot> neighs = mibHelper.getNeighs();
    
    if (cellSnapshots.isEmpty() || neighs.isEmpty()) {
      LOGGER.debug("No cells or neighbors available, skipping step");
      throw new Exception();
    }
    
    for (NanocellMibHelper.CellSnapshot cellSnap : cellSnapshots) {
      //LOGGER.info("MobilityEngine:buildStructure");
      AuxCell cellAux = new AuxCell(cellSnap);
      cellAux.computeWeights(filterNeighBySrc(cellAux.cellId, neighs));
      aux.add(cellAux);
    }
    return aux;
  }

  private void performMobilityStep(List<AuxCell> cellsList) {
    for (AuxCell cell : cellsList) {
      LOGGER.info("performMobilityStep cell: " +cell.cellId + " list size: " + cellsList.size());
      double moveRatio = ThreadLocalRandom.current().nextDouble(MOVE_RATIO_MIN, MOVE_RATIO_MAX) / 100; 
      double totalUesToMove = cell.cellCurrentUsers * moveRatio;
      for (Map.Entry<Integer, Double> entry : cell.computedWeights.entrySet()) {
        long uesToMove = Math.round(totalUesToMove * entry.getValue());
        AuxCell neighbor = getCellById(entry.getKey(), cellsList);
        if (uesToMove > neighbor.capacityDelta) {
            uesToMove = neighbor.capacityDelta;
        }
        if (uesToMove <= 0 || cell.cellCurrentUsers - uesToMove < 0) {
          LOGGER.info("No UE will be moved from " +cell.cellId +" to " +neighbor.cellId);
          continue;
        }
        this.mibHelper.moveUEs(cell.cellId, neighbor.cellId, (int)uesToMove);
        cell.removeUEs(uesToMove);
        neighbor.addUEs(uesToMove);
      }
    }
  }

  private AuxCell getCellById(int id, List<AuxCell> cellsList) {
    AuxCell rtn = null;

    for (AuxCell cell : cellsList) {
      if(id == cell.cellId) {
        rtn = cell;
        break;
      }
    }
    return rtn;
  }

  private List<NanocellMibHelper.NeighSnapshot> filterNeighBySrc(int srcId, List<NanocellMibHelper.NeighSnapshot> neighs) {
    List<NanocellMibHelper.NeighSnapshot> filter = new ArrayList<>();

    for (NanocellMibHelper.NeighSnapshot neigh : neighs) {
      if (neigh.sourceCellId == srcId)
        filter.add(neigh);
    }
    return filter;
  }

  private static final class AuxCell {
    public int cellId;
    public long cellMaxUsers;
    public long cellCurrentUsers;
    public long capacityDelta;
    public Map<Integer, Double> computedWeights;
    public int nNeighbors;

    AuxCell(NanocellMibHelper.CellSnapshot snapshot) {
      this.cellId = snapshot.cellId;
      this.cellMaxUsers = snapshot.cellMaxUsers;
      this.cellCurrentUsers = snapshot.cellCurrentUsers;
      this.capacityDelta = snapshot.cellMaxUsers - snapshot.cellCurrentUsers;
      this.computedWeights = new HashMap<>();
      this.nNeighbors = 0;
    }

    public void removeUEs(long ues) {
      cellCurrentUsers = Math.max(0, this.cellCurrentUsers - ues);
      capacityDelta = Math.max(0, this.cellMaxUsers - this.cellCurrentUsers);
    }

    public void addUEs(long ues) {
      cellCurrentUsers = this.cellCurrentUsers + ues;
      capacityDelta = Math.max(0, this.cellMaxUsers - this.cellCurrentUsers);
    }
    
    public boolean computeWeights(List<NanocellMibHelper.NeighSnapshot> neighList) {
      double total_weight = 0.0;
      boolean rtn = true;

      if (!computedWeights.isEmpty())
        return !rtn;
      for (NanocellMibHelper.NeighSnapshot neigh : neighList) {
        computedWeights.put(neigh.neighborCellId, (double)neigh.weight);
        total_weight += (double)neigh.weight;
        nNeighbors++;
      }
      for (Map.Entry<Integer, Double> entry : computedWeights.entrySet()) {
        double val = entry.getValue() / total_weight;
        computedWeights.replace(entry.getKey(), val);
      }
      return rtn;
    }

    public long capDelta() {
      return Math.max(0, this.cellMaxUsers - this.cellCurrentUsers);
    }
  }

}
