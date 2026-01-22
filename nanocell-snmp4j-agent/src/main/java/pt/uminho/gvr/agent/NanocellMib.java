package pt.uminho.gvr.agent;

 

//--AgentGen BEGIN=_BEGIN
//--AgentGen END

import org.snmp4j.smi.*;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.agent.*;
import org.snmp4j.agent.mo.*;
import org.snmp4j.agent.mo.snmp.*;
import org.snmp4j.agent.mo.snmp.smi.*;
import org.snmp4j.agent.request.*;
import org.snmp4j.log.LogFactory;
import org.snmp4j.log.LogAdapter;
import org.snmp4j.agent.mo.snmp.tc.*;



//--AgentGen BEGIN=_IMPORT
//--AgentGen END

/**
 * {@code NanocellMib} implements a {@link MOGroup} with the following SNMP MIB SMI
 * description:
 * A simple MIB module for a nano-cell mobility simulation.
 * 
 * The MIB models a fixed set of cells (base stations) and
 * neighbor relations between them. The SNMP agent simulates
 * user (UE) mobility by moving users between cells according
 * to configured neighbor weights. The total number of UEs in
 * the system is constant; only their distribution across cells
 * changes over time.
 * 
 * This MIB is intended for educational use with Python (PySNMP)
 * and Java (SNMP4J) tutorials.
 */
public class NanocellMib 
//--AgentGen BEGIN=_EXTENDS
//--AgentGen END
implements MOGroup 
//--AgentGen BEGIN=_IMPLEMENTS
//--AgentGen END
{

  private static final LogAdapter LOGGER = 
      LogFactory.getLogger(NanocellMib.class);

//--AgentGen BEGIN=_STATIC
//--AgentGen END

  // Factory
  private MOFactory moFactory = 
    DefaultMOFactory.getInstance();

  // Constants 

  /**
   * OID of this MIB module for usage which can be 
   * used for its identification.
   */
  public static final OID oidNanocellMib =
    new OID(new int[] { 1,3,6,1,4,1,88888 });

  // Identities
  // Scalars
  /**
   * {@code oidTotalUEs} is the {@link OID} of totalUEs:
   * Total number of user equipments (UEs) in the system.
   * 
   * This value is fixed for the duration of the simulation.
   * The agent maintains a constant number of UEs and only
   * redistributes them between cells.
   */
  public static final OID oidTotalUEs = 
    new OID(new int[] { 1,3,6,1,4,1,88888,1,1,1,0 });
  /**
   * {@code oidMobilityInterval} is the {@link OID} of mobilityInterval:
   * The time interval between mobility updates in the simulation,
   * expressed in hundredths of a second (1/100 s).
   * 
   * For example, a value of 500 represents 5 seconds between
   * simulation steps. Adjusting this value allows the manager to
   * speed up or slow down the mobility process.
   */
  public static final OID oidMobilityInterval = 
    new OID(new int[] { 1,3,6,1,4,1,88888,1,1,2,0 });
  // Tables

  // Notifications

  // Enumerations




  // TextualConventions
  private static final String TC_MODULE_SNMPV2_TC = "SNMPv2-TC";
  private static final String TC_DISPLAYSTRING = "DisplayString";

  // Scalars
  private MOScalar<UnsignedInteger32> totalUEs;
  private MOScalar mobilityInterval;

  // Tables
  public static final OID oidCellEntry = 
    new OID(new int[] { 1,3,6,1,4,1,88888,1,1,3,1 });

  // Index OID definitions
  public static final OID oidCellId =
    new OID(new int[] { 1,3,6,1,4,1,88888,1,1,3,1,1 });

  // Column TC definitions for cellEntry:
  public static final String tcModuleSNMPv2Tc = "SNMPv2-TC";
  public static final String tcDefDisplayString = "DisplayString";
    
  // Column sub-identifier definitions for cellEntry:
  public static final int colCellName = 2;
  public static final int colCellMaxUsers = 3;
  public static final int colCellCurrentUsers = 4;
  public static final int colCellHandoversInTotal = 5;
  public static final int colCellHandoversOutTotal = 6;

  // Column index definitions for cellEntry:
  public static final int idxCellName = 0;
  public static final int idxCellMaxUsers = 1;
  public static final int idxCellCurrentUsers = 2;
  public static final int idxCellHandoversInTotal = 3;
  public static final int idxCellHandoversOutTotal = 4;

  private MOTableSubIndex[] cellEntryIndexes;
  private MOTableIndex cellEntryIndex;

  @SuppressWarnings(value={"rawtypes"})
  private MOTable<CellEntryRow, MOColumn,
    MOTableModel<CellEntryRow>> cellEntry;
  private MOTableModel<CellEntryRow> cellEntryModel;
  public static final OID oidCellNeighborEntry = 
    new OID(new int[] { 1,3,6,1,4,1,88888,1,2,1,1 });

  // Index OID definitions
  public static final OID oidSourceCellId =
    new OID(new int[] { 1,3,6,1,4,1,88888,1,2,1,1,1 });
  public static final OID oidNeighborCellId =
    new OID(new int[] { 1,3,6,1,4,1,88888,1,2,1,1,2 });

  // Column TC definitions for cellNeighborEntry:
    
  // Column sub-identifier definitions for cellNeighborEntry:
  public static final int colNeighborWeight = 3;

  // Column index definitions for cellNeighborEntry:
  public static final int idxNeighborWeight = 0;

  private MOTableSubIndex[] cellNeighborEntryIndexes;
  private MOTableIndex cellNeighborEntryIndex;

  @SuppressWarnings(value={"rawtypes"})
  private MOTable<CellNeighborEntryRow, MOColumn,
    MOTableModel<CellNeighborEntryRow>> cellNeighborEntry;
  private MOTableModel<CellNeighborEntryRow> cellNeighborEntryModel;


//--AgentGen BEGIN=_MEMBERS
//--AgentGen END

  /**
   * Constructs a NanocellMib instance without actually creating its
   * {@code ManagedObject} instances. This has to be done in a
   * sub-class constructor or after construction by calling 
   * {@link #createMO(MOFactory moFactory)}. 
   */
  protected NanocellMib() {
//--AgentGen BEGIN=_DEFAULTCONSTRUCTOR
//--AgentGen END
  }

  /**
   * Constructs a NanocellMib instance and actually creates its
   * {@code ManagedObject} instances using the supplied 
   * {@code MOFactory} (by calling
   * {@link #createMO(MOFactory moFactory)}).
   * @param moFactory
   *    the {@code MOFactory} to be used to create the
   *    managed objects for this module.
   */
  public NanocellMib(MOFactory moFactory) {
  	this();
    //--AgentGen BEGIN=_FACTORYCONSTRUCTOR::factoryWrapper
    //--AgentGen END
  	this.moFactory = moFactory;
    createMO(moFactory);
//--AgentGen BEGIN=_FACTORYCONSTRUCTOR
//--AgentGen END
  }

//--AgentGen BEGIN=_CONSTRUCTORS
//--AgentGen END

  /**
   * Create the ManagedObjects defined for this MIB module
   * using the specified {@link MOFactory}.
   * @param moFactory
   *    the {@code MOFactory} instance to use for object 
   *    creation.
   */
  protected void createMO(MOFactory moFactory) {
    addTCsToFactory(moFactory);
    totalUEs = 
      moFactory.createScalar(oidTotalUEs,
                             moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY), 
                             new UnsignedInteger32());
    mobilityInterval = 
      new MobilityInterval(oidMobilityInterval, 
                           moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_WRITE));
    mobilityInterval.addMOValueValidationListener(new MobilityIntervalValidator());
    createCellEntry(moFactory);
    createCellNeighborEntry(moFactory);
  }

  /**
   * Gets the scalar totalUEs with the OID 1.3.6.1.4.1.88888.1.1.1.
   * @return
   *    a MOScalar<UnsignedInteger32> instance.
   */
  public MOScalar<UnsignedInteger32> getTotalUEs() {
    return totalUEs;
  }
  /**
   * Gets the scalar mobilityInterval with the OID 1.3.6.1.4.1.88888.1.1.2.
   * 
   * @return
   *    a MOScalar instance.
   */
  public MOScalar getMobilityInterval() {
    return mobilityInterval;
  }


    @SuppressWarnings(value={"rawtypes"})
    public MOTable<CellEntryRow,MOColumn,MOTableModel<CellEntryRow>> getCellEntry() {
        return cellEntry;
    }


    @SuppressWarnings(value={"unchecked"})
    private void createCellEntry(MOFactory moFactory) {
        // Index definition
    cellEntryIndexes = 
      new MOTableSubIndex[] {
      moFactory.createSubIndex(oidCellId, 
                               SMIConstants.SYNTAX_INTEGER, 1, 1)
    };

    cellEntryIndex = 
      moFactory.createIndex(cellEntryIndexes,
                            false,
                            new MOTableIndexValidator() {
      public boolean isValidIndex(OID index) {
        boolean isValidIndex = true;
    //--AgentGen BEGIN=cellEntry::isValidIndex
    //--AgentGen END
        return isValidIndex;
      }
    });

        // Columns
        MOColumn<?>[] cellEntryColumns = new MOColumn<?>[5];
        cellEntryColumns[idxCellName] =
        moFactory.createColumn(colCellName,
                               SMIConstants.SYNTAX_OCTET_STRING,
                               moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY),
                               tcModuleSNMPv2Tc,
                               tcDefDisplayString);
        cellEntryColumns[idxCellMaxUsers] =
        moFactory.createColumn(colCellMaxUsers,
                               SMIConstants.SYNTAX_GAUGE32,
                               moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY));
        cellEntryColumns[idxCellCurrentUsers] =
        moFactory.createColumn(colCellCurrentUsers,
                               SMIConstants.SYNTAX_GAUGE32,
                               moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY));
        cellEntryColumns[idxCellHandoversInTotal] =
        moFactory.createColumn(colCellHandoversInTotal,
                               SMIConstants.SYNTAX_COUNTER32,
                               moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY));
        cellEntryColumns[idxCellHandoversOutTotal] =
        moFactory.createColumn(colCellHandoversOutTotal,
                               SMIConstants.SYNTAX_COUNTER32,
                               moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_ONLY));
        // Table model
        cellEntryModel =
            moFactory.createTableModel(oidCellEntry,
                                       cellEntryIndex,
                                       cellEntryColumns);
        ((MOMutableTableModel<CellEntryRow>)cellEntryModel).setRowFactory(
            new CellEntryRowFactory());
        cellEntry =
            moFactory.createTable(oidCellEntry,
                                  cellEntryIndex,
                                  cellEntryColumns,
                                  cellEntryModel);
  }

    @SuppressWarnings(value={"rawtypes"})
    public MOTable<CellNeighborEntryRow,MOColumn,MOTableModel<CellNeighborEntryRow>> getCellNeighborEntry() {
        return cellNeighborEntry;
    }


    @SuppressWarnings(value={"unchecked"})
    private void createCellNeighborEntry(MOFactory moFactory) {
        // Index definition
    cellNeighborEntryIndexes = 
      new MOTableSubIndex[] {
      moFactory.createSubIndex(oidSourceCellId, 
                               SMIConstants.SYNTAX_INTEGER, 1, 1),
      moFactory.createSubIndex(oidNeighborCellId, 
                               SMIConstants.SYNTAX_INTEGER, 1, 1)
    };

    cellNeighborEntryIndex = 
      moFactory.createIndex(cellNeighborEntryIndexes,
                            false,
                            new MOTableIndexValidator() {
      public boolean isValidIndex(OID index) {
        boolean isValidIndex = true;
    //--AgentGen BEGIN=cellNeighborEntry::isValidIndex
    //--AgentGen END
        return isValidIndex;
      }
    });

        // Columns
        MOColumn<?>[] cellNeighborEntryColumns = new MOColumn<?>[1];
        cellNeighborEntryColumns[idxNeighborWeight] =
        new MOMutableColumn<UnsignedInteger32>(colNeighborWeight,
                          SMIConstants.SYNTAX_GAUGE32,
                          moFactory.createAccess(MOAccessImpl.ACCESSIBLE_FOR_READ_WRITE),
                          (UnsignedInteger32)null
    //--AgentGen BEGIN=neighborWeight::auxInit
    //--AgentGen END
          );
        ((MOMutableColumn<?>)cellNeighborEntryColumns[idxNeighborWeight]).
        addMOValueValidationListener(new NeighborWeightValidator());
        // Table model
        cellNeighborEntryModel =
            moFactory.createTableModel(oidCellNeighborEntry,
                                       cellNeighborEntryIndex,
                                       cellNeighborEntryColumns);
        ((MOMutableTableModel<CellNeighborEntryRow>)cellNeighborEntryModel).setRowFactory(
            new CellNeighborEntryRowFactory());
        cellNeighborEntry =
            moFactory.createTable(oidCellNeighborEntry,
                                  cellNeighborEntryIndex,
                                  cellNeighborEntryColumns,
                                  cellNeighborEntryModel);
  }


  @Override
  public void registerMOs(MOServer server, OctetString context) 
    throws DuplicateRegistrationException 
  {
    // Scalar Objects
    server.register(this.totalUEs, context);
    server.register(this.mobilityInterval, context);
    server.register(this.cellEntry, context);
    server.register(this.cellNeighborEntry, context);
//--AgentGen BEGIN=_registerMOs
//--AgentGen END
  }

  @Override
  public void unregisterMOs(MOServer server, OctetString context) {
    // Scalar Objects
    server.unregister(this.totalUEs, context);
    server.unregister(this.mobilityInterval, context);
    server.unregister(this.cellEntry, context);
    server.unregister(this.cellNeighborEntry, context);
//--AgentGen BEGIN=_unregisterMOs
//--AgentGen END
  }

  // Notifications

  // Scalars
  public class MobilityInterval extends MOScalar<TimeTicks> {
    MobilityInterval(OID oid, MOAccess access) {
      super(oid, access, new TimeTicks());
//--AgentGen BEGIN=mobilityInterval
//--AgentGen END
    }

    public int isValueOK(SubRequest<?> request) {
      Variable newValue =
        request.getVariableBinding().getVariable();
      int valueOK = super.isValueOK(request);
      if (valueOK != SnmpConstants.SNMP_ERROR_SUCCESS) {
      	return valueOK;
      }
    //--AgentGen BEGIN=mobilityInterval::isValueOK
    //--AgentGen END
      return valueOK; 
    }

    public TimeTicks getValue() {
    //--AgentGen BEGIN=mobilityInterval::getValue
    //--AgentGen END
      return super.getValue();    
    }

    public int setValue(TimeTicks newValue) {
    //--AgentGen BEGIN=mobilityInterval::setValue
    //--AgentGen END
      return super.setValue(newValue);    
    }

    //--AgentGen BEGIN=mobilityInterval::_METHODS
    //--AgentGen END

  }


  // Value Validators
  /**
   * The {@code MobilityIntervalValidator} implements the value
   * validation for {@code MobilityInterval}.
   */
  static class MobilityIntervalValidator implements MOValueValidationListener {
    
    public void validate(MOValueValidationEvent validationEvent) {
      Variable newValue = validationEvent.getNewValue();
    //--AgentGen BEGIN=mobilityInterval::validate
    //--AgentGen END
    }
  }

  /**
   * The {@code NeighborWeightValidator} implements the value
   * validation for {@code NeighborWeight}.
   */
  static class NeighborWeightValidator implements MOValueValidationListener {
    
    public void validate(MOValueValidationEvent validationEvent) {
      Variable newValue = validationEvent.getNewValue();
    //--AgentGen BEGIN=neighborWeight::validate
    //--AgentGen END
    }
  }

  // Rows and Factories

  /**
   * {@code CellEntryRow} implements a {@link DefaultMOMutableRow2PC} for the following table SMI
   * description:
   * An entry describing a single cell in the simulation.
   */
  public class CellEntryRow extends DefaultMOMutableRow2PC {

    //--AgentGen BEGIN=cellEntry::RowMembers
    //--AgentGen END

    /**
     * Create a new row with the provided row index sub-identifier and values.
     * @param index
     *    the row index sub-identifier (without table OID).
     * @param values
     *    the variable values for the new row, empty columns must be provided with {@code null} values.
     */
    public CellEntryRow(OID index, Variable[] values) {
      super(index, values);
    //--AgentGen BEGIN=cellEntry::RowConstructor
    //--AgentGen END
    }
    
    /**
     * Get the column value for:
     * A human-readable name for this cell, such as a label or
     * logical identifier.
     * @return
     *    this columns value.
     */
    public OctetString getCellName() {
    //--AgentGen BEGIN=cellEntry::getCellName
    //--AgentGen END
      return (OctetString) super.getValue(idxCellName);
    }

    /**
     * Set the column value for this column:
     * A human-readable name for this cell, such as a label or
     * logical identifier.
     * @param newColValue
     *    the new column value.
     */
    public void setCellName(OctetString newColValue) {
    //--AgentGen BEGIN=cellEntry::setCellName
    //--AgentGen END
      super.setValue(idxCellName, newColValue);
    }
    
    /**
     * Get the column value for:
     * The maximum number of users (UEs) that this cell is allowed
     * to host simultaneously. The simulation logic should not
     * assign more than this number of UEs to the cell.
     * @return
     *    this columns value.
     */
    public UnsignedInteger32 getCellMaxUsers() {
    //--AgentGen BEGIN=cellEntry::getCellMaxUsers
    //--AgentGen END
      return (UnsignedInteger32) super.getValue(idxCellMaxUsers);
    }

    /**
     * Set the column value for this column:
     * The maximum number of users (UEs) that this cell is allowed
     * to host simultaneously. The simulation logic should not
     * assign more than this number of UEs to the cell.
     * @param newColValue
     *    the new column value.
     */
    public void setCellMaxUsers(UnsignedInteger32 newColValue) {
    //--AgentGen BEGIN=cellEntry::setCellMaxUsers
    //--AgentGen END
      super.setValue(idxCellMaxUsers, newColValue);
    }
    
    /**
     * Get the column value for:
     * The current number of users (UEs) assigned to this cell.
     * 
     * This value is updated dynamically by the agent as users move
     * between cells in the simulation. The sum of cellCurrentUsers
     * over all cells should be equal to totalUEs.
     * @return
     *    this columns value.
     */
    public Gauge32 getCellCurrentUsers() {
    //--AgentGen BEGIN=cellEntry::getCellCurrentUsers
    //--AgentGen END
      return (Gauge32) super.getValue(idxCellCurrentUsers);
    }

    /**
     * Set the column value for this column:
     * The current number of users (UEs) assigned to this cell.
     * 
     * This value is updated dynamically by the agent as users move
     * between cells in the simulation. The sum of cellCurrentUsers
     * over all cells should be equal to totalUEs.
     * @param newColValue
     *    the new column value.
     */
    public void setCellCurrentUsers(Gauge32 newColValue) {
    //--AgentGen BEGIN=cellEntry::setCellCurrentUsers
    //--AgentGen END
      super.setValue(idxCellCurrentUsers, newColValue);
    }
    
    /**
     * Get the column value for:
     * The total number of users that have arrived in this cell via
     * handover from other cells since the agent started.
     * @return
     *    this columns value.
     */
    public Counter32 getCellHandoversInTotal() {
    //--AgentGen BEGIN=cellEntry::getCellHandoversInTotal
    //--AgentGen END
      return (Counter32) super.getValue(idxCellHandoversInTotal);
    }

    /**
     * Set the column value for this column:
     * The total number of users that have arrived in this cell via
     * handover from other cells since the agent started.
     * @param newColValue
     *    the new column value.
     */
    public void setCellHandoversInTotal(Counter32 newColValue) {
    //--AgentGen BEGIN=cellEntry::setCellHandoversInTotal
    //--AgentGen END
      super.setValue(idxCellHandoversInTotal, newColValue);
    }
    
    /**
     * Get the column value for:
     * The total number of users that have left this cell via
     * handover to other cells since the agent started.
     * @return
     *    this columns value.
     */
    public Counter32 getCellHandoversOutTotal() {
    //--AgentGen BEGIN=cellEntry::getCellHandoversOutTotal
    //--AgentGen END
      return (Counter32) super.getValue(idxCellHandoversOutTotal);
    }

    /**
     * Set the column value for this column:
     * The total number of users that have left this cell via
     * handover to other cells since the agent started.
     * @param newColValue
     *    the new column value.
     */
    public void setCellHandoversOutTotal(Counter32 newColValue) {
    //--AgentGen BEGIN=cellEntry::setCellHandoversOutTotal
    //--AgentGen END
      super.setValue(idxCellHandoversOutTotal, newColValue);
    }
    
    /**
     * Get the column value for:
     * @param column
     *    the zero-based index of the column.
     * @return
     *    this columns value.
     */
    public Variable getValue(int column) {
    //--AgentGen BEGIN=cellEntry::RowGetValue
    //--AgentGen END
        switch(column) {
            case idxCellName:
        	    return getCellName();
            case idxCellMaxUsers:
        	    return getCellMaxUsers();
            case idxCellCurrentUsers:
        	    return getCellCurrentUsers();
            case idxCellHandoversInTotal:
        	    return getCellHandoversInTotal();
            case idxCellHandoversOutTotal:
        	    return getCellHandoversOutTotal();
            default:
                return super.getValue(column);
        }
    }

    /**
     * Set the column value for this column:
     * @param column
     *    the zero-based index of the column to be set.
     * @param value
     *    the new column value.
     */
    public void setValue(int column, Variable value) {
    //--AgentGen BEGIN=cellEntry::RowSetValue
    //--AgentGen END
        switch(column) {
            case idxCellName:
        	    setCellName((OctetString)value);
        	    break;
            case idxCellMaxUsers:
        	    setCellMaxUsers((UnsignedInteger32)value);
        	    break;
            case idxCellCurrentUsers:
        	    setCellCurrentUsers((Gauge32)value);
        	    break;
            case idxCellHandoversInTotal:
        	    setCellHandoversInTotal((Counter32)value);
        	    break;
            case idxCellHandoversOutTotal:
        	    setCellHandoversOutTotal((Counter32)value);
        	    break;
            default:
                super.setValue(column, value);
            }
        }

    //--AgentGen BEGIN=cellEntry::Row
    //--AgentGen END
    }

    /**
     * {@code CellEntryRowFactory} implements a {@link MOTableRowFactory} for the following table SMI
     * description:
     * An entry describing a single cell in the simulation.
     */
    public class CellEntryRowFactory implements MOTableRowFactory<CellEntryRow>
    {
        public synchronized CellEntryRow createRow(OID index, Variable[] values)
            throws UnsupportedOperationException
        {
            CellEntryRow row = new CellEntryRow(index, values);
    //--AgentGen BEGIN=cellEntry::createRow
    //--AgentGen END
            return row;
        }
    
        public synchronized void freeRow(CellEntryRow row) {
    //--AgentGen BEGIN=cellEntry::freeRow
    //--AgentGen END
        }

    //--AgentGen BEGIN=cellEntry::RowFactory
    //--AgentGen END
    }

  /**
   * {@code CellNeighborEntryRow} implements a {@link DefaultMOMutableRow2PC} for the following table SMI
   * description:
   * An entry describing a directed neighbor relation from a
   * source cell to a neighbor cell. The agent uses the
   * neighborWeight to drive weighted random mobility of users
   * between cells.
   */
  public class CellNeighborEntryRow extends DefaultMOMutableRow2PC {

    //--AgentGen BEGIN=cellNeighborEntry::RowMembers
    //--AgentGen END

    /**
     * Create a new row with the provided row index sub-identifier and values.
     * @param index
     *    the row index sub-identifier (without table OID).
     * @param values
     *    the variable values for the new row, empty columns must be provided with {@code null} values.
     */
    public CellNeighborEntryRow(OID index, Variable[] values) {
      super(index, values);
    //--AgentGen BEGIN=cellNeighborEntry::RowConstructor
    //--AgentGen END
    }
    
    /**
     * Get the column value for:
     * The relative weight used when selecting this neighbor as a
     * target for user mobility from the source cell.
     * 
     * For a given sourceCellId, the agent considers all neighbor
     * entries with that same sourceCellId and interprets their
     * neighborWeight values as a set of weights in a weighted
     * random choice. Neighbors with higher weights are more likely
     * to receive users from the source cell.
     * 
     * A value of zero indicates that this neighbor relation should
     * not be used for mobility (i.e., it is effectively disabled).
     * @return
     *    this columns value.
     */
    public UnsignedInteger32 getNeighborWeight() {
    //--AgentGen BEGIN=cellNeighborEntry::getNeighborWeight
    //--AgentGen END
      return (UnsignedInteger32) super.getValue(idxNeighborWeight);
    }

    /**
     * Set the column value for this column:
     * The relative weight used when selecting this neighbor as a
     * target for user mobility from the source cell.
     * 
     * For a given sourceCellId, the agent considers all neighbor
     * entries with that same sourceCellId and interprets their
     * neighborWeight values as a set of weights in a weighted
     * random choice. Neighbors with higher weights are more likely
     * to receive users from the source cell.
     * 
     * A value of zero indicates that this neighbor relation should
     * not be used for mobility (i.e., it is effectively disabled).
     * @param newColValue
     *    the new column value.
     */
    public void setNeighborWeight(UnsignedInteger32 newColValue) {
    //--AgentGen BEGIN=cellNeighborEntry::setNeighborWeight
    //--AgentGen END
      super.setValue(idxNeighborWeight, newColValue);
    }
    
    /**
     * Get the column value for:
     * @param column
     *    the zero-based index of the column.
     * @return
     *    this columns value.
     */
    public Variable getValue(int column) {
    //--AgentGen BEGIN=cellNeighborEntry::RowGetValue
    //--AgentGen END
        switch(column) {
            case idxNeighborWeight:
        	    return getNeighborWeight();
            default:
                return super.getValue(column);
        }
    }

    /**
     * Set the column value for this column:
     * @param column
     *    the zero-based index of the column to be set.
     * @param value
     *    the new column value.
     */
    public void setValue(int column, Variable value) {
    //--AgentGen BEGIN=cellNeighborEntry::RowSetValue
    //--AgentGen END
        switch(column) {
            case idxNeighborWeight:
        	    setNeighborWeight((UnsignedInteger32)value);
        	    break;
            default:
                super.setValue(column, value);
            }
        }

    //--AgentGen BEGIN=cellNeighborEntry::Row
    //--AgentGen END
    }

    /**
     * {@code CellNeighborEntryRowFactory} implements a {@link MOTableRowFactory} for the following table SMI
     * description:
     * An entry describing a directed neighbor relation from a
     * source cell to a neighbor cell. The agent uses the
     * neighborWeight to drive weighted random mobility of users
     * between cells.
     */
    public class CellNeighborEntryRowFactory implements MOTableRowFactory<CellNeighborEntryRow>
    {
        public synchronized CellNeighborEntryRow createRow(OID index, Variable[] values)
            throws UnsupportedOperationException
        {
            CellNeighborEntryRow row = new CellNeighborEntryRow(index, values);
    //--AgentGen BEGIN=cellNeighborEntry::createRow
    //--AgentGen END
            return row;
        }
    
        public synchronized void freeRow(CellNeighborEntryRow row) {
    //--AgentGen BEGIN=cellNeighborEntry::freeRow
    //--AgentGen END
        }

    //--AgentGen BEGIN=cellNeighborEntry::RowFactory
    //--AgentGen END
    }


//--AgentGen BEGIN=_METHODS
//--AgentGen END

  // Textual Definitions of MIB module NanocellMib
  protected void addTCsToFactory(MOFactory moFactory) {
  }


//--AgentGen BEGIN=_TC_CLASSES_IMPORTED_MODULES_BEGIN
//--AgentGen END

  // Textual Definitions of other MIB modules
  public void addImportedTCsToFactory(MOFactory moFactory) {
  }


//--AgentGen BEGIN=_TC_CLASSES_IMPORTED_MODULES_END
//--AgentGen END

//--AgentGen BEGIN=_CLASSES
//--AgentGen END

//--AgentGen BEGIN=_END
//--AgentGen END
}

