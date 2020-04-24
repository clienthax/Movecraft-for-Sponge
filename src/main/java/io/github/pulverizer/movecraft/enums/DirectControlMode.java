package io.github.pulverizer.movecraft.enums;

/**
 * An Enum of Direct Control Modes.
 */
public enum DirectControlMode {
    OFF {
        @Override
        public String toString() {
            return "OFF";
        }
    },

    A {
        @Override
        public String toString() {
            return "A";
        }
    },

    B {
        @Override
        public String toString() {
            return "B";
        }
    }
}