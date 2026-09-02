# X-Star Remote-Controller Firmware

## Preserved source packages

Two independently recovered X-Star Premium aggregate packages now provide a
controller-firmware comparison set:

| Aggregate package | SHA-256 | RC component | RC version |
|---|---|---|---|
| `X3P_FW_900M_V1.1.3.bin` | `63096382d1cb252edc18efc67065e1a16f37b2f445a2eca08863a1bbd30f5d2b` | `X3P_RC_V1.0.0.37_20160406.bin` | V1.0.0.37 |
| `X3P_FW_900M_V2.0.12.bin` | `fe6c66bed25ac01395f3b9082accddf2989cd7a91e848fecb61e82d9b82a64d7` | `X3P_RC_V1.0.1.5_20170713.BIN` | V1.0.1.5 |

Both aggregate packages pass all 17 component MD5 checks with
`tools/firmware/extract_xstar_firmware.py`. The second package confirms that the
known final X-Star Premium remote-controller version is V1.0.1.5. This is also
the version named by the preserved SDK capability record.

The proprietary package and extracted component binaries remain in the private,
Git-ignored research archive. They are not committed to the public repository.

## RC-PRO wrapper

Both extracted controller images use the same wrapper:

```text
offset  size  observed meaning
0x00    4     02 AA 55 AA magic
0x1C    4     version, little-endian packed as V1.0.1.5
0x20    4     unpadded inner-payload length
0x24    4     integrity/check field; algorithm not yet identified
0x28    4     target value 7 in both recovered images
0x2C    4     duplicate version field
0x30    ...   NUL-terminated product tag "RC-PRO"
0xF0    ...   inner payload, rounded up to a 16-byte boundary
```

The declared inner length is exact: rounding it up to the next 16-byte boundary
and adding the `0xF0` wrapper reproduces the full component length in both
releases.

## Inner-payload finding

The inner payload is a deterministic 16-byte-block transform. It contains many
identical 16-byte blocks, and the most common ciphertext block is identical in
both controller releases:

```text
7D F7 6B 0C 1A B8 99 B3 3E 42 F0 47 B9 1B 54 6F
```

It occurs 8,233 times in V1.0.0.37 and 5,747 times in V1.0.1.5. Several other
high-frequency ciphertext blocks also recur at comparable counts across both
versions. This is strong evidence for a fixed-key, ECB-like 16-byte block
transform over an MCU flash image. AES-128 ECB is a leading hypothesis, but it
must not be labeled proven until the updater/bootloader implementation or key is
recovered and a full plaintext image passes structural and integrity checks.

This finding explains why ordinary `file` and `strings` inspection does not yet
expose the MCU architecture, USB descriptors, stick scanning, or button map.

## Reproducing the inspection

After extracting both aggregate packages into private directories:

```bash
python tools/firmware/analyze_rc_firmware.py \
  X3P_RC_V1.0.0.37_20160406.bin \
  --compare X3P_RC_V1.0.1.5_20170713.BIN
```

The analyzer is read-only. It validates the wrapper, declared length, version
duplication and 16-byte padding, then reports entropy and repeated-block data.

## Simulator relevance

The V1.0.1.5 image is now the correct target for controller interoperability
research. Once decoded, the highest-value items are:

1. USB descriptor and CDC command handling;
2. ADC/stick acquisition and calibration tables;
3. physical-button and gimbal-wheel scan maps;
4. controller-to-aircraft command-frame construction;
5. the safest location for a simulator-only output mode, if the hardware and
   recovery path make such a change defensible.

Firmware modification is not the first experiment. A decoded and validated
image, bootloader/recovery characterization, full backup path, and a reversible
bench procedure are prerequisites before any write to the physical controller.

## Next evidence target

Recover the code that consumes the RC-PRO wrapper. The likely locations are the
controller bootloader, an earlier controller dump, or a matching updater/native
library. The repeated-block evidence supplies a known regression test: a
candidate decoder must turn both releases into structurally valid MCU images and
must reproduce the header integrity field or another independent checksum.
