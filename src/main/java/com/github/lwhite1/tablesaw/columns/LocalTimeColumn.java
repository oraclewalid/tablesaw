package com.github.lwhite1.tablesaw.columns;

import com.github.lwhite1.tablesaw.api.Table;
import com.github.lwhite1.tablesaw.api.ColumnType;
import com.github.lwhite1.tablesaw.columns.packeddata.PackedLocalTime;
import com.github.lwhite1.tablesaw.filter.IntBiPredicate;
import com.github.lwhite1.tablesaw.filter.IntPredicate;
import com.github.lwhite1.tablesaw.filter.LocalTimePredicate;
import com.github.lwhite1.tablesaw.io.TypeUtils;
import com.github.lwhite1.tablesaw.store.ColumnMetadata;
import com.github.lwhite1.tablesaw.util.ReverseIntComparator;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.roaringbitmap.RoaringBitmap;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A column in a base table that contains float values
 */
public class LocalTimeColumn extends AbstractColumn implements IntIterable {

  public static final int MISSING_VALUE = (int) ColumnType.LOCAL_TIME.getMissingValue();

  private static int DEFAULT_ARRAY_SIZE = 128;

  private IntArrayList data;

  public static LocalTimeColumn create(String name) {
    return new LocalTimeColumn(name);
  }

  public static LocalTimeColumn create(String fileName, IntArrayList times) {
    LocalTimeColumn column = new LocalTimeColumn(fileName, times.size());
    column.data = times;
    return column;
  }

  private LocalTimeColumn(String name) {
    super(name);
    data = new IntArrayList(DEFAULT_ARRAY_SIZE);
  }

  public LocalTimeColumn(ColumnMetadata metadata) {
    super(metadata);
    data = new IntArrayList(DEFAULT_ARRAY_SIZE);
  }

  public LocalTimeColumn(String name, int initialSize) {
    super(name);
    data = new IntArrayList(initialSize);
  }

  public int size() {
    return data.size();
  }

  public void add(int f) {
    data.add(f);
  }

  @Override
  public ColumnType type() {
    return ColumnType.LOCAL_TIME;
  }

  @Override
  public String getString(int row) {
    return PackedLocalTime.toShortTimeString(getInt(row));
  }

  @Override
  public LocalTimeColumn emptyCopy() {
    return new LocalTimeColumn(name());
  }

  @Override
  public LocalTimeColumn emptyCopy(int rowSize) {
    return new LocalTimeColumn(name(), rowSize);
  }

  @Override
  public void clear() {
    data.clear();
  }

  private LocalTimeColumn copy() {
    return LocalTimeColumn.create(name(), data);
  }

  @Override
  public void sortAscending() {
    Arrays.parallelSort(data.elements());
  }

  @Override
  public void sortDescending() {
    IntArrays.parallelQuickSort(data.elements(), reverseIntComparator);
  }

  IntComparator reverseIntComparator = new IntComparator() {

    @Override
    public int compare(Integer o2, Integer o1) {
      return (o1 < o2 ? -1 : (o1.equals(o2) ? 0 : 1));
    }

    @Override
    public int compare(int o2, int o1) {
      return (o1 < o2 ? -1 : (o1 == o2 ? 0 : 1));
    }
  };

  @Override
  public Table summary() {

    Int2IntOpenHashMap counts = new Int2IntOpenHashMap();

    for (int i = 0; i < size(); i++) {
      int value;
      int next = getInt(i);
      if (next == Integer.MIN_VALUE) {
        value = LocalTimeColumn.MISSING_VALUE;
      } else {
        value = next;
      }
      if (counts.containsKey(value)) {
        counts.addTo(value, 1);
      } else {
        counts.put(value, 1);
      }
    }
    Table table = new Table("Column: " + name());
    table.addColumn(LocalTimeColumn.create("Time"));
    table.addColumn(IntColumn.create("Count"));

    for (Int2IntMap.Entry entry : counts.int2IntEntrySet()) {
      table.localTimeColumn(0).add(entry.getIntKey());
      table.intColumn(1).add(entry.getIntValue());
    }
    table = table.sortDescendingOn("Count");

    return table.first(5);
  }

  @Override
  public int countUnique() {
    IntSet ints = new IntOpenHashSet(data);
    return ints.size();
  }

  @Override
  public LocalTimeColumn unique() {
    IntSet ints = new IntOpenHashSet(data);
    return LocalTimeColumn.create(name() + " Unique values", IntArrayList.wrap(ints.toIntArray()));
  }

  @Override
  public boolean isEmpty() {
    return data.isEmpty();
  }

  public static int convert(String value) {
    if (Strings.isNullOrEmpty(value)
        || TypeUtils.MISSING_INDICATORS.contains(value)
        || value.equals("-1")) {
      return (int) ColumnType.LOCAL_TIME.getMissingValue();
    }
    value = Strings.padStart(value, 4, '0');
    return PackedLocalTime.pack(LocalTime.parse(value, TypeUtils.TIME_FORMATTER));
  }

  @Override
  public void addCell(String object) {
    try {
      add(convert(object));
    } catch (NullPointerException e) {
      throw new RuntimeException(name() + ": "
          + String.valueOf(object) + ": "
          + e.getMessage());
    }
  }

  public int getInt(int index) {
    return data.getInt(index);
  }

  public LocalTime get(int index) {
    return PackedLocalTime.asLocalTime(getInt(index));
  }

  @Override
  public IntComparator rowComparator() {
    return comparator;
  }

  IntComparator comparator = new IntComparator() {

    @Override
    public int compare(Integer r1, Integer r2) {
      return compare((int) r1, (int) r2);
    }

    @Override
    public int compare(int r1, int r2) {
      int f1 = getInt(r1);
      int f2 = getInt(r2);
      return Integer.compare(f1, f2);
    }
  };

  public RoaringBitmap isEqualTo(LocalTime value) {
    RoaringBitmap results = new RoaringBitmap();
    int packedLocalTime = PackedLocalTime.pack(value);
    int i = 0;
    for (int next : data) {
      if (packedLocalTime == next) {
        results.add(i);
      }
      i++;
    }
    return results;
  }

  public String print() {
    StringBuilder builder = new StringBuilder();
    builder.append(title());
    for (int next : data) {
      builder.append(String.valueOf(PackedLocalTime.asLocalTime(next)));
      builder.append('\n');
    }
    return builder.toString();
  }

  public IntArrayList data() {
    return data;
  }

  @Override
  public String toString() {
    return "LocalTime column: " + name();
  }

  public LocalTimeColumn selectIf(LocalTimePredicate predicate) {
    LocalTimeColumn column = emptyCopy();
    IntIterator iterator = iterator();
    while (iterator.hasNext()) {
      int next = iterator.nextInt();
      if (predicate.test(PackedLocalTime.asLocalTime(next))) {
        column.add(next);
      }
    }
    return column;
  }

  /**
   * This version operates on predicates that treat the given IntPredicate as operating on a packed local time
   * This is much more efficient that using a LocalTimePredicate, but requires that the developer understand the
   * semantics of packedLocalTimes
   */
  public LocalTimeColumn selectIf(IntPredicate predicate) {
    LocalTimeColumn column = emptyCopy();
    IntIterator iterator = iterator();
    while (iterator.hasNext()) {
      int next = iterator.nextInt();
      if (predicate.test(next)) {
        column.add(next);
      }
    }
    return column;
  }

  @Override
  public void append(Column column) {
    Preconditions.checkArgument(column.type() == this.type());
    LocalTimeColumn intColumn = (LocalTimeColumn) column;
    for (int i = 0; i < intColumn.size(); i++) {
      add(intColumn.getInt(i));
    }
  }

  public RoaringBitmap isMidnight() {
    return apply(PackedLocalTime::isMidnight);
  }

  public RoaringBitmap isNoon() {
    return apply(PackedLocalTime::isNoon);
  }

  /**
   * Applies a function to every value in this column that returns true if the time is in the AM or "before noon".
   * Note: we follow the convention that 12:00 NOON is PM and 12 MIDNIGHT is AM
   */
  public RoaringBitmap AM() {
    return apply(PackedLocalTime::AM);
  }

  /**
   * Applies a function to every value in this column that returns true if the time is in the PM or "after noon".
   * Note: we follow the convention that 12:00 NOON is PM and 12 MIDNIGHT is AM
   */
  public RoaringBitmap PM() {
    return apply(PackedLocalTime::PM);
  }

  /**
   * Returns the largest ("top") n values in the column
   *
   * @param n The maximum number of records to return. The actual number will be smaller if n is greater than the
   *          number of observations in the column
   * @return A list, possibly empty, of the largest observations
   */
  public List<LocalTime> max(int n) {
    List<LocalTime> top = new ArrayList<>();
    int[] values = data.toIntArray();
    IntArrays.parallelQuickSort(values, ReverseIntComparator.instance());
    for (int i = 0; i < n && i < values.length; i++) {
      top.add(PackedLocalTime.asLocalTime(values[i]));
    }
    return top;
  }

  /**
   * Returns the smallest ("bottom") n values in the column
   *
   * @param n The maximum number of records to return. The actual number will be smaller if n is greater than the
   *          number of observations in the column
   * @return A list, possibly empty, of the smallest n observations
   */
  public List<LocalTime> min(int n) {
    List<LocalTime> bottom = new ArrayList<>();
    int[] values = data.toIntArray();
    IntArrays.parallelQuickSort(values);
    for (int i = 0; i < n && i < values.length; i++) {
      bottom.add(PackedLocalTime.asLocalTime(values[i]));
    }
    return bottom;
  }

  public IntIterator iterator() {
    return data.iterator();
  }

  public RoaringBitmap apply(IntPredicate predicate) {
    RoaringBitmap bitmap = new RoaringBitmap();
    for (int idx = 0; idx < data.size(); idx++) {
      int next = data.getInt(idx);
      if (predicate.test(next)) {
        bitmap.add(idx);
      }
    }
    return bitmap;
  }

  public RoaringBitmap apply(IntBiPredicate predicate, int value) {
    RoaringBitmap bitmap = new RoaringBitmap();
    for (int idx = 0; idx < data.size(); idx++) {
      int next = data.getInt(idx);
      if (predicate.test(next, value)) {
        bitmap.add(idx);
      }
    }
    return bitmap;
  }
}