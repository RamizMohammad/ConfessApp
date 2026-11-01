/**
 * java.util.Random, bit for bit — the Android banners place their stars, waves and letters from
 * a fixed seed, so the web must draw the same banner from the same numbers.
 */
const MULT = 0x5DEECE66Dn;
const MASK = (1n << 48n) - 1n;

export class JavaRandom {
  private seed: bigint;

  constructor(seed: number | bigint) {
    this.seed = (BigInt.asIntN(64, BigInt(seed)) ^ MULT) & MASK;
  }

  private next(bits: number): number {
    this.seed = (this.seed * MULT + 0xBn) & MASK;
    return Number(BigInt.asIntN(32, this.seed >> BigInt(48 - bits)));
  }

  nextInt(bound?: number): number {
    if (bound === undefined) return this.next(32);
    if (bound <= 0) throw new RangeError("bound must be positive");
    if ((bound & -bound) === bound) return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    let bits: number, val: number;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (bits - val + (bound - 1) > 0x7fffffff);       // Java's int overflow check
    return val;
  }

  nextFloat(): number {
    return this.next(24) / (1 << 24);
  }

  nextBoolean(): boolean {
    return this.next(1) !== 0;
  }
}

/** Java's String.hashCode (32-bit, signed). */
export function javaHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(31, h) + s.charCodeAt(i)) | 0;
  return h;
}

/** Java's Math.floorMod for ints. */
export function floorMod(a: number, n: number): number {
  return ((a % n) + n) % n;
}
