package Final;

public class TaskQueue {
    private TotalPath.PathExplorer[] array;
    private int size;
    private static final int DEFAULT_CAPACITY = 64;  // Default capacity of the queue
    private static final float LOAD_FACTOR = 0.90f; // Resize the array when 90% full

    //  Create a Taskqueue with default capacity
    public TaskQueue() {
        array = new TotalPath.PathExplorer[DEFAULT_CAPACITY];
        size = 0;
    }

    // Adding task to the queue
    public void add(TotalPath.PathExplorer task) {
        // Check if we need to resize before adding
        if (size >= array.length * LOAD_FACTOR) {
            resize(array.length * 2);
        }
        array[size++] = task;
    }

    // Get all the current task as an array
    public TotalPath.PathExplorer[] getAll() {
        TotalPath.PathExplorer[] result = new TotalPath.PathExplorer[size];
        System.arraycopy(array, 0, result, 0, size);

        return result;
    }

    // Check if the queue is empty
    public boolean isEmpty() {
        return size == 0;
    }

    // Get the size of the queue
    public int size() {
        return size;
    }

    // Resize the array to the new capacity
    private void resize(int newCapacity) {
        TotalPath.PathExplorer[] newArray = new TotalPath.PathExplorer[newCapacity];
        System.arraycopy(array, 0, newArray, 0, size);

        // Clear old array references to help Garbage collection
        for (int i = 0; i < size; i++) {
            array[i] = null;
        }

        array = newArray;
    }
}