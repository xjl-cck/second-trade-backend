package org.example.secondhandtradingplatform.product.canal;

import com.alibaba.otter.canal.protocol.CanalEntry.Column;
import com.alibaba.otter.canal.protocol.CanalEntry.Entry;
import com.alibaba.otter.canal.protocol.CanalEntry.EntryType;
import com.alibaba.otter.canal.protocol.CanalEntry.EventType;
import com.alibaba.otter.canal.protocol.CanalEntry.RowChange;
import com.alibaba.otter.canal.protocol.CanalEntry.RowData;
import com.google.protobuf.ByteString;
import org.example.secondhandtradingplatform.product.service.impl.ProductSearchIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CanalProductIndexListenerTest {

    private ProductSearchIndex searchIndex;
    private CanalProductIndexListener listener;

    @BeforeEach
    void setUp() {
        searchIndex = mock(ProductSearchIndex.class);
        when(searchIndex.index(anyLong(), any(), any(), any())).thenReturn(true);
        when(searchIndex.delete(anyLong())).thenReturn(true);
        listener = new CanalProductIndexListener(searchIndex, new CanalProperties());
    }

    @Test
    @DisplayName("UPDATE 把标题/描述/地点写进索引")
    void indexesUpdatedRow() {
        CanalProductIndexListener.Counts counts = apply(EventType.UPDATE, row(7L, "0"));

        assertEquals(1, counts.indexed);
        assertEquals(0, counts.failed);
        verify(searchIndex).index(7L, "二手自行车", "九成新", "东门");
    }

    @Test
    @DisplayName("is_deleted 变成 1 时删文档")
    void removesLogicallyDeletedRow() {
        CanalProductIndexListener.Counts counts = apply(EventType.UPDATE, row(8L, "1"));

        assertEquals(1, counts.removed);
        verify(searchIndex).delete(8L);
        verify(searchIndex, never()).index(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("物理 DELETE 用 before 镜像里的 id 删文档")
    void removesPhysicallyDeletedRow() {
        RowData rowData = RowData.newBuilder()
                .addBeforeColumns(column("id", "9"))
                .build();
        Entry entry = entry(EventType.DELETE, rowData);

        CanalProductIndexListener.Counts counts = new CanalProductIndexListener.Counts();
        listener.apply(entry, counts);

        assertEquals(1, counts.removed);
        verify(searchIndex).delete(9L);
    }

    @Test
    @DisplayName("ES 写失败只计数，不抛异常，整批照常 ack")
    void countsFailureWithoutThrowing() {
        when(searchIndex.index(anyLong(), any(), any(), any())).thenReturn(false);

        CanalProductIndexListener.Counts counts = apply(EventType.UPDATE, row(10L, "0"));

        assertEquals(0, counts.indexed);
        assertEquals(1, counts.failed);
    }

    @Test
    @DisplayName("非 ROWDATA 的 entry（心跳/事务头）直接忽略")
    void ignoresNonRowDataEntry() {
        Entry entry = Entry.newBuilder()
                .setEntryType(EntryType.TRANSACTIONBEGIN)
                .setStoreValue(ByteString.EMPTY)
                .build();

        CanalProductIndexListener.Counts counts = new CanalProductIndexListener.Counts();
        listener.apply(entry, counts);

        assertEquals(0, counts.indexed + counts.removed + counts.failed);
        verify(searchIndex, never()).index(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("无法解析的 binlog body 计入失败而不是把线程搞挂")
    void survivesBrokenPayload() {
        Entry entry = Entry.newBuilder()
                .setEntryType(EntryType.ROWDATA)
                .setStoreValue(ByteString.copyFromUtf8("not-a-row-change"))
                .build();

        CanalProductIndexListener.Counts counts = new CanalProductIndexListener.Counts();
        listener.apply(entry, counts);

        assertEquals(1, counts.failed);
    }

    private CanalProductIndexListener.Counts apply(EventType eventType, RowData rowData) {
        CanalProductIndexListener.Counts counts = new CanalProductIndexListener.Counts();
        listener.apply(entry(eventType, rowData), counts);
        return counts;
    }

    private Entry entry(EventType eventType, RowData rowData) {
        RowChange rowChange = RowChange.newBuilder()
                .setEventType(eventType)
                .addRowDatas(rowData)
                .build();
        return Entry.newBuilder()
                .setEntryType(EntryType.ROWDATA)
                .setStoreValue(rowChange.toByteString())
                .build();
    }

    private RowData row(Long id, String isDeleted) {
        return RowData.newBuilder()
                .addAfterColumns(column("id", String.valueOf(id)))
                .addAfterColumns(column("title", "二手自行车"))
                .addAfterColumns(column("description", "九成新"))
                .addAfterColumns(column("location", "东门"))
                .addAfterColumns(column("is_deleted", isDeleted))
                .build();
    }

    private Column column(String name, String value) {
        return Column.newBuilder().setName(name).setValue(value).setIsNull(false).build();
    }
}
