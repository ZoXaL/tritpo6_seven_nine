package com.yatty.sevennine.ai;

import java.util.Random;

public enum Difficulty {
    EASY (1000, 3000, "Petya", "Vanya", "Vasya"),
//    EASY (100, 200, "Petya", "Vanya", "Vasya"),
    MEDIUM (500, 2500, "Petr", "Ivan", "Vasiliy"),
    HARD (300, 1500, "Petr-Petrovich", "Ivan-Vasilyevich", "Vasiliy-Ivanovich");
    protected String[] names;
    protected long delayMin;
    protected long delayMax;
    private Random random;
    
    Difficulty(long delayMin, long delayMax, String ... names) {
        this.names = names;
        this.delayMin = delayMin;
        this.delayMax = delayMax;
        random = new Random();
    }
    
    public String getName() {
        int nameIndex = Math.abs(random.nextInt()) % names.length;
        return names[nameIndex];
    }
    
    public long getDelay() {
        return Math.abs(random.nextLong()) % (delayMax - delayMin) + delayMin;
    }
}
