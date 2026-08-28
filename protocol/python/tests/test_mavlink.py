from xstar_protocol.mavlink import scan_mavlink, summarize


def test_scans_minimal_v1_frame():
    # magic,len,seq,sys,comp,msg,crc-lo,crc-hi
    data = bytes([0xFE, 0x00, 7, 1, 2, 0, 0xAA, 0x55])
    frames = scan_mavlink(data)
    assert len(frames) == 1
    f = frames[0]
    assert f.version == 1
    assert f.sequence == 7
    assert f.system_id == 1
    assert f.component_id == 2
    assert f.message_id == 0
    assert f.frame_length == 8


def test_scans_minimal_v2_frame():
    # magic,len,incompat,compat,seq,sys,comp,msgid3,crc2
    data = bytes([0xFD, 0x00, 0, 0, 9, 3, 4, 0, 0, 0, 0x12, 0x34])
    frames = scan_mavlink(data)
    assert len(frames) == 1
    f = frames[0]
    assert f.version == 2
    assert f.sequence == 9
    assert f.system_id == 3
    assert f.component_id == 4
    assert f.message_id == 0
    assert f.frame_length == 12


def test_v2_signed_frame_accounts_for_signature():
    base = bytes([0xFD, 0x00, 1, 0, 1, 1, 1, 42, 0, 0, 0, 0])
    data = base + bytes(13)
    frames = scan_mavlink(data)
    assert len(frames) == 1
    assert frames[0].signed is True
    assert frames[0].frame_length == 25


def test_noise_is_ignored():
    assert scan_mavlink(b"hello world") == []


def test_summary_groups_messages_and_components():
    data = (
        bytes([0xFE, 0, 1, 1, 1, 0, 0, 0])
        + bytes([0xFE, 0, 2, 1, 1, 30, 0, 0])
    )
    result = summarize(scan_mavlink(data))
    assert result["count"] == 2
    assert result["versions"] == {1: 2}
    assert result["message_ids"] == {0: 1, 30: 1}
    assert result["systems"] == {"1:1": 2}
