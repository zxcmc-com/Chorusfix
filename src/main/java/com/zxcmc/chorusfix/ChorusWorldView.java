package com.zxcmc.chorusfix;

@FunctionalInterface
public interface ChorusWorldView {
  ChorusMaterial typeAt(int dx, int dy, int dz);
}
