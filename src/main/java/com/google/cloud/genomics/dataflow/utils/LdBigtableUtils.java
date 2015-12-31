/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.genomics.dataflow.utils;

import org.apache.hadoop.hbase.util.Bytes;

/**
 * A helper class that provides static methods for transforming linkage disequilibrium data to and
 * from Cloud BigTable.
 */
public class LdBigtableUtils {

  // The single column family of the LD BigTable
  public static final byte[] FAMILY = Bytes.toBytes("ld");

  // All column qualifiers of the LD BigTable
  public static final byte[] QUALIFIER = Bytes.toBytes("result");

  // The maximum human chromosome size is ~250,000,000 bp, so an int's worth of bits is plenty.
  private static final int MIN_POS = 0;
  private static final int MAX_POS = 2147483647;
  private static final String MIN_ALLELE = "*";
  private static final String MAX_ALLELE = "~";

  /**
   * Returns a byte array representing the BigTable row key based on the variant attributes.
   */
  public static byte[] key(String qChrom, int qPosition, String qZeroAllele, String qOneAllele,
      String tChrom, int tPosition, String tZeroAllele, String tOneAllele) {
    byte[] queryByteArray = getVariantKey(qChrom, qPosition, qZeroAllele, qOneAllele);
    byte[] targetByteArray = getVariantKey(tChrom, tPosition, tZeroAllele, tOneAllele);
    return Bytes.add(queryByteArray, targetByteArray);
  }

  /**
   * Returns a BigTable row key that, as a start row, will include all rows on the chromosome.
   */
  public static byte[] keyStart(String chromosome) {
    return keyStart(chromosome, MIN_POS);
  }

  /**
   * Returns a BigTable row key that, as a start row, will include all rows on the chromosome
   * beginning at or after the given position.
   */
  public static byte[] keyStart(String chromosome, int position) {
    return keyStart(chromosome, position, MIN_ALLELE, MIN_ALLELE);
  }

  /**
   * Returns a BigTable row key that, as a start row, will include all rows on the chromosome
   * beginning at or after the given position and with the given alleles.
   */
  public static byte[] keyStart(String chromosome, int position, String zeroAllele, String oneAllele) {
    return key(
        chromosome, position, zeroAllele, oneAllele,
        chromosome, MIN_POS, MIN_ALLELE, MIN_ALLELE);
  }

  /**
   * Returns a BigTable row key that, as an end row, will include all rows on the chromosome.
   */
  public static byte[] keyEnd(String chromosome) {
    return keyEnd(chromosome, MAX_POS);
  }

  /**
   * Returns a BigTable row key that, as an end row, will include all rows on the chromosome
   * up to and including the given position.
   */
  public static byte[] keyEnd(String chromosome, int position) {
    return keyEnd(chromosome, position, MAX_ALLELE, MAX_ALLELE);
  }

  /**
   * Returns a BigTable row key that, as an end row, will include all rows on the chromosome
   * up to and including the given position and alleles.
   */
  public static byte[] keyEnd(String chromosome, int position, String zeroAllele, String oneAllele) {
    return key(
        chromosome, position, zeroAllele, oneAllele,
        chromosome, MAX_POS, MAX_ALLELE, MAX_ALLELE);
  }

  /**
   * Returns a byte array representing a hash of one variant based on its chromosome, position, and
   * alleles.
   */
  private static byte[] getVariantKey(String chrom, int position, String zeroAllele,
      String oneAllele) {
    long result = 0L;
    int chromHash = simpleSkewed16BitHash(chrom.hashCode());
    result |= (((long) chromHash) << 48);
    result |= (((long) position) << 16);
    result |= ((long) alleleHash(zeroAllele, oneAllele));
    return Bytes.toBytes(result);
  }

  /**
   * Returns a 16-bit integer hash of the two alleles.
   */
  private static int alleleHash(String zeroAllele, String oneAllele) {
    String toHash = String.format("%s,%s", zeroAllele, oneAllele);
    return simpleSkewed16BitHash(toHash.hashCode());
  }

  /**
   * Returns a 16-bit integer hash of the 32-bit hash value.
   *
   * <p>
   * This function is known to be a non-uniform hash. However, since in practice we are using this
   * to hash chromosome names (~25-100 total, depending on genome build used) and alleles of
   * variants located at the same genomic position, it is exceedingly unlikely already that hash
   * values will collide.
   */
  private static int simpleSkewed16BitHash(int fullHash) {
    int truncation = fullHash % 32768;
    if (truncation < 0) {
      truncation = 32767 - truncation;  // Now it is in [0, 65536)
    }
    return truncation & 0x0000FFFF;
  }

  // Prevents instantiation.
  private LdBigtableUtils() {}
}
