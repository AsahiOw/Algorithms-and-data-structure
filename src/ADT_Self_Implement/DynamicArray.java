package ADT_Self_Implement;

public class DynamicArray {
    private long[] array;
    private int size;
    private static final int DEFAULT_CAPACITY = 16;  // Increased from 10 for better power-of-2 alignment
    private static final float GROWTH_FACTOR = 1.5f; // Changed from 2x to 1.5x for more gradual growth

    public DynamicArray() {
        // Round to next power of 2 for better memory alignment
        int capacity = DEFAULT_CAPACITY;
        array = new long[capacity];
        size = 0;
    }

    public DynamicArray(int initialCapacity) {
        // Ensure minimum capacity and round to next power of 2
        int capacity = Math.max(DEFAULT_CAPACITY,
                nextPowerOfTwo(initialCapacity));
        array = new long[capacity];
        size = 0;
    }

    // Fast bit manipulation to get next power of 2
    private static int nextPowerOfTwo(int value) {
        value--;
        value |= value >> 1;
        value |= value >> 2;
        value |= value >> 4;
        value |= value >> 8;
        value |= value >> 16;
        return value + 1;
    }

    public void add(long element) {
        ensureCapacity(size + 1);
        array[size++] = element;
    }

    public long get(int index) {
        // No bounds checking in release mode for performance
        // Uncomment the following lines for debug mode
        /*if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }*/
        return array[index];
    }

    public void set(int index, long element) {
        // No bounds checking in release mode for performance
        // Uncomment the following lines for debug mode
        /*if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
        }*/
        array[index] = element;
    }

    public int size() {
        return size;
    }

    private void ensureCapacity(int minCapacity) {
        if (minCapacity > array.length) {
            int newCapacity = Math.max(
                    minCapacity,
                    (int)(array.length * GROWTH_FACTOR)
            );
            // Round to next power of 2 for better memory alignment
            newCapacity = nextPowerOfTwo(newCapacity);
            resize(newCapacity);
        }
    }

    private void resize(int newCapacity) {
        // Direct array copy is faster than System.arraycopy for small arrays
        long[] newArray = new long[newCapacity];
        if (size < 128) {
            for (int i = 0; i < size; i++) {
                newArray[i] = array[i];
            }
        } else {
            System.arraycopy(array, 0, newArray, 0, size);
        }
        array = newArray;
    }

    // Optional: Add trimToSize method to reduce memory usage if needed
    public void trimToSize() {
        if (size < array.length) {
            array = java.util.Arrays.copyOf(array, size);
        }
    }
}