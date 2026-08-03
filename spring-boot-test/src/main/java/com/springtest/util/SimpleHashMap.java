package com.springtest.util;

public class SimpleHashMap {

    // 1. This is the LinkedList Node
    static class Node {
        String key;
        String value;
        Node next; // Pointer to the next node (making it a LinkedList)

        Node(String key, String value) {
            this.key = key;
            this.value = value;
            this.next = null;
        }
    }

    // 2. The HashMap internally uses an Array of these Nodes
    private Node[] buckets;

    public SimpleHashMap(int capacity) {
        buckets = new Node[capacity]; // e.g., size is 4
    }

    // A simple hash function to find the array index
    private int getIndex(String key) {
        // Just using the string length modulo the array size to force collisions easily
        return key.length() % buckets.length;
    }

    // 3. Inserting data into the Map
    public void put(String key, String value) {
        int index = getIndex(key);
        Node newNode = new Node(key, value);

        // If the array slot is empty, put the first node there
        if (buckets[index] == null) {
            buckets[index] = newNode;
        }
        // COLLISION: If occupied, we append to the LinkedList at this bucket
        else {
            Node temp = buckets[index];
            // Walk to the end of the LinkedList
            while (temp.next != null) {
                temp = temp.next;
            }
            // Link the new node to the end
            temp.next = newNode;
        }
    }

    // 4. Printing the exact internal structure of our Map
    public void printMapStructure() {
        for (int i = 0; i < buckets.length; i++) {
            System.out.print("Bucket Array Index [" + i + "]: ");

            Node temp = buckets[i];
            if (temp == null) {
                System.out.println("null");
            } else {
                // Loop through the LinkedList in this bucket
                while (temp != null) {
                    System.out.print("[" + temp.key + "=" + temp.value + "]");
                    if (temp.next != null) {
                        System.out.print(" -> "); // Visually showing the 'next' pointer
                    }
                    temp = temp.next;
                }
                System.out.println(" -> null");
            }
        }
    }

    public static void main(String[] args) {
        SimpleHashMap map = new SimpleHashMap(4); // Create a map with 4 slots

        // "John" and "Alex" both have 4 letters.
        // Hash function: 4 % 4 = index 0. They will COLLIDE.
        map.put("John", "Engineer");
        map.put("Alex", "Manager");

        // "Sam" has 3 letters.
        // Hash function: 3 % 4 = index 3. No collision.
        map.put("Sam", "Designer");

        // Print the map to see how they look in memory
        map.printMapStructure();
    }
}
