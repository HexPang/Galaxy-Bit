<template>
  <div class="app-container home">
<!--    <span v-for="(v, k) in torrents" :key="'torrent_' + k">{{ v.title }}</span>-->
    <el-menu v-if="categoryTree" :default-active="menuIndex" mode="horizontal" background-color="#304156"
             text-color="#fff" active-text-color="#ffd04b" @select="handleMenuSelect"
             style="border-radius: 10px; overflow: hidden; margin-bottom: 10px;">
      <el-menu-item index="0">全部</el-menu-item>
      <el-submenu style="padding-left: 20px;" v-for="(v, k) in categoryTree" :key="'parent_' + k" :index="v.id + ''">
        <template slot="title">{{ v.title }}</template>
        <div v-for="(v1, k1) in v.children" :key="'parent_sub_' + k1">
          <el-submenu v-if="v1.children.length > 0"  :index="v1.id + ''">
            <template slot="title">{{ v1.title }}</template>
            <el-menu-item v-for="(v2, k2) in v1.children" :key="'menu_item_' + k2" :index="v2.id + ''">{{ v2.title }}</el-menu-item>
          </el-submenu>
          <el-menu-item v-else :index="v1.id + ''">{{ v1.title }}</el-menu-item>
        </div>
      </el-submenu>
      <el-input
        style="width: 300px; float: right; margin-right: 10px; margin-top: 12px;"
        placeholder="请输入内容"
        prefix-icon="el-icon-search"
        v-model="queryParams.title">
        <el-button slot="append" icon="el-icon-search" type="primary" @click="handleSearch"></el-button>
      </el-input>
    </el-menu>
    <el-carousel height="24px" style="margin-bottom: 5px; padding: 5px;color: #1890ff;" v-if="notices" indicator-position="none" arrow="never">
      <el-carousel-item v-for="(v, k) in notices" :key="'notice_' + k" style="cursor: pointer" @click.native="showNotice(v)">
        <span style="line-height: 24px;">{{ v.noticeTitle }}</span>
      </el-carousel-item>
    </el-carousel>
    <el-card v-loading="loading">
      <div class="folder-box" v-if="torrents !== null && torrents.length > 0">
        <torrent-card :torrent="v" v-for="(v, k) in torrents" :key="'torrent_' + k" @click.native="handleTorrentClick(v)"></torrent-card>
      </div>
      <div v-else style="text-align: center; font-size: 24px;height: 300px; color: #5c5c5c;">
        <i class="el-icon-ice-tea" style="line-height: 300px;">这里竟然空空如也, 骚年不如来分享下?</i>
      </div>
    </el-card>
    <div>
      <el-pagination
        style="float: right;"
        @current-change="handleCurrentChange"
        :current-page.sync="queryParams.pageNum"
        :page-size="queryParams.pageSize"
        layout="total, prev, pager, next"
        :total="page.total">
      </el-pagination>
    </div>
    <torrent-dialog :torrent-id="torrentId" @closed="handleClosed"/>
    <el-dialog :title="notice ? notice.noticeTitle : '公告'" :visible.sync="shouldShowNoticeDialog" width="80%;">
      <div v-html="notice.noticeContent" v-if="notice && shouldShowNoticeDialog"></div>
    </el-dialog>
  </div>
</template>

<script>
  import TorrentDialog from "@/components/TorrentDialog"
  import TorrentCard from "@/components/TorrentCard"
  import TorrentViewer from "@/components/TorrentViewer"
  import { listCategory } from "@/api/galaxy/category";
  import { listTorrents, getNotice } from "@/api/galaxy/piazza"
  export default {
    name: "index",
    components: {
      TorrentViewer,
      TorrentCard,
      TorrentDialog
    },
    data() {
      return {
        notice: null,
        menuIndex: null,
        page: {
          total: 0
        },
        queryParams: {
          pageNum: 1,
          pageSize: 20,
          infoHash: null,
          title: null,
          categories: null,
          status: null,
          orderByColumn: 'updateTime',
          isAsc: 'desc'
        },
        noticeQuery: {
          pageNum: 1,
          pageSize: 10,
          noticeTitle: undefined,
          createBy: undefined,
          status: undefined
        },
        categories: null,
        torrents: null,
        categoryTree: null,
        torrentId: null,
        notices: null,
        shouldShowNoticeDialog: false,
        loading: false
      };
    },
    methods: {
      handleSearch () {
        this.queryParams.pageNum = 1
        this.handleCurrentChange()
      },
      showNotice (notice) {
        this.notice = notice
        this.shouldShowNoticeDialog = true
      },
      loadNotice () {
        getNotice(this.noticeQuery).then( res => {
          this.notices = res.rows
        })
      },
      handleCurrentChange () {
        this.loading = true
        listTorrents(this.queryParams).then(res => {
          this.loading = false
          res.rows.forEach( x => {
            x.categoryName = this.getCategoryName(x.categories)
          })
          this.torrents = res.rows
          this.$set(this.page, 'total', res.total)
        })
      },
      handleClosed () {
        this.torrentId = null
      },
      handleTorrentClick (torrent) {
        this.torrentId = torrent.id
      },
      handleMenuSelect (index) {
        if (parseInt(index) === 0) {
          index = null
        }
        this.queryParams.categories = index
        this.handleCurrentChange()
      },
      getCategoryName (id) {
        let category = this.categories.find(x => {
          return parseInt(x.id) === parseInt(id)
        })
        if (category) {
          return category.title
        }
        return null
      },
      load () {
        listCategory().then(res => {
          this.categories = res.data
          this.categoryTree = this.handleTree(res.data)
        }).then(() => {
          this.handleCurrentChange()
        })
        this.loadNotice()
      }
    },
    created() {
      this.load()
    }
  };
</script>
<style scoped>
  /deep/ .el-dialog__body img {
    max-width: 100% !important;
  }
</style>
<style scoped lang="scss">
  .folder-box {
    display: flex;
    display: -webkit-flex;
    flex-direction: row;
    flex-wrap: wrap;
    align-content: flex-start;
    align-items: center;
  }
  .folder-item {
    width: 250px;
    overflow: hidden;
    cursor: pointer;
    text-align: center;
    img {
      height: 250px;
    }
  }
  .folder-item:hover > span {
    color: #3A71A8;
  }
  .home {
    blockquote {
      padding: 10px 20px;
      margin: 0 0 20px;
      font-size: 17.5px;
      border-left: 5px solid #eee;
    }

    hr {
      margin-top: 20px;
      margin-bottom: 20px;
      border: 0;
      border-top: 1px solid #eee;
    }

    .col-item {
      margin-bottom: 20px;
    }

    ul {
      padding: 0;
      margin: 0;
    }

    font-family: "open sans", "Helvetica Neue", Helvetica, Arial, sans-serif;
    font-size: 13px;
    color: #676a6c;
    overflow-x: hidden;

    ul {
      list-style-type: none;
    }

    h4 {
      margin-top: 0px;
    }

    h2 {
      margin-top: 10px;
      font-size: 26px;
      font-weight: 100;
    }

    p {
      margin-top: 10px;

      b {
        font-weight: 700;
      }
    }

    .update-log {
      ol {
        display: block;
        list-style-type: decimal;
        margin-block-start: 1em;
        margin-block-end: 1em;
        margin-inline-start: 0;
        margin-inline-end: 0;
        padding-inline-start: 40px;
      }
    }
  }
</style>

