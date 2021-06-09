<template>
  <div class="folder-item">
    <img :src="torrent.thumburl">
    <el-tooltip :content="torrent.title">
      <div class="item-title">{{ torrent.title }}</div>
    </el-tooltip>
    <div>
      <el-tag size="mini" style="margin: 5px;" type="success">{{ torrent.categoryName }}</el-tag>
      <el-tag size="mini" style="margin: 5px;">{{ formatFileSize(torrent.fileSize) }}</el-tag>
    </div>
  </div>
</template>

<script>
  export default {
    props: {
      torrent: {
        type: Object,
        default: null
      }
    },
    data () {
      return {}
    }
  }
</script>
<style scoped lang="scss">
  .folder-item {
    width: 250px;
    overflow: hidden;
    cursor: pointer;
    text-align: center;
    border-radius: 10px;
    padding: 5px;
    height: 320px;
    img {
      height: 250px;
      /*max-width: 100%;*/
      width: 170px;
      border-radius: 5px;
    }
    .item-title {
      height: 34px;
      line-height: 34px;
      text-overflow:ellipsis;
      overflow: hidden;
      white-space: nowrap;
      -o-text-overflow: ellipsis;
    }
  }
  .folder-item:hover {
    background-color: rgba(233,233,233,0.8);
  }
</style>
