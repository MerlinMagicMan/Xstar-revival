from xstar_protocol.stream import MavlinkStreamScanner


def test_reassembles_v1_across_usb_reads():
    frame = bytes([0xFE, 0, 7, 1, 2, 0, 0xAA, 0x55])
    scanner = MavlinkStreamScanner()
    assert scanner.feed(frame[:3]) == []
    frames = scanner.feed(frame[3:])
    assert len(frames) == 1
    assert frames[0].raw == frame
    assert frames[0].offset == 0


def test_skips_noise_and_reassembles_v2():
    frame = bytes([0xFD, 0, 0, 0, 9, 3, 4, 30, 0, 0, 0x12, 0x34])
    scanner = MavlinkStreamScanner()
    assert scanner.feed(b"noise" + frame[:5]) == []
    frames = scanner.feed(frame[5:])
    assert len(frames) == 1
    assert frames[0].version == 2
    assert frames[0].message_id == 30
    assert frames[0].offset == 5
