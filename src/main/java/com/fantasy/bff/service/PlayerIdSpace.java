package com.fantasy.bff.service;

/**
 * Which platform's numbering a pool's player ids are. Yahoo and ESPN give the same person
 * different numbers, so a projection saved from one pool cannot be read against the other —
 * which is why every stored projection records the space its rows were filled from.
 */
public enum PlayerIdSpace {
    YAHOO,
    ESPN
}
