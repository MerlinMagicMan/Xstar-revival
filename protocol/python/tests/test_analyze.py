from xstar_protocol.analyze import analyze


def test_analyze_mixed_capture():
    mav = bytes([0xFE, 0, 1, 1, 1, 0, 0, 0])
    data = b"noise" + mav + b"RTSP/1.0 200 OK\r\n" + b"\x00\x00\x00\x01\x67x"
    result = analyze(data)
    assert result["mavlink"]["count"] == 1
    assert result["mavlink_frames"][0]["message_id"] == 0
    assert {h["kind"] for h in result["signatures"]} == {"rtsp", "h264"}
