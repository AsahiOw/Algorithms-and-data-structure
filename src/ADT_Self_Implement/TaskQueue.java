package ADT_Self_Implement;

public class TaskQueue {
    private TotalPath2.PathExplorer[] array;
    private int size;
    private static final int DEFAULT_CAPACITY = 32;  // Depend on situation, currently set it to this to reduce the resize
    private static final int SMALL_ARRAY_THRESHOLD = 64;
    private static final float LOAD_FACTOR = 0.75f;

    public TaskQueue() {
        array = new TotalPath2.PathExplorer[DEFAULT_CAPACITY];
        size = 0;
    }

    public void add(TotalPath2.PathExplorer task) {
        // Check if we need to resize before adding
        if (size >= array.length * LOAD_FACTOR) {
            resize(array.length * 2);
        }
        array[size++] = task;
    }

    public TotalPath2.PathExplorer[] getAll() {
        // For very small arrays, direct copy is faster
        if (size < SMALL_ARRAY_THRESHOLD) {
            TotalPath2.PathExplorer[] result = new TotalPath2.PathExplorer[size];
            System.arraycopy(array, 0, result, 0, size);
            return result;
        }
        return java.util.Arrays.copyOf(array, size);
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }

    private void resize(int newCapacity) {
        TotalPath2.PathExplorer[] newArray = new TotalPath2.PathExplorer[newCapacity];

        // For small arrays, manual copy is faster than System.arraycopy
        if (size < SMALL_ARRAY_THRESHOLD) {
            if (size >= 0) System.arraycopy(array, 0, newArray, 0, size);
        } else {
            System.arraycopy(array, 0, newArray, 0, size);
        }

        // Clear old array references to help GC
        for (int i = 0; i < size; i++) {
            array[i] = null;
        }

        array = newArray;
    }

}