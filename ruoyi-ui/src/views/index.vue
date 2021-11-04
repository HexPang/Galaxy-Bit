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
    <torrent-list ref="torrentList"/>
    <el-dialog :title="notice ? notice.noticeTitle : '公告'" :visible.sync="shouldShowNoticeDialog" width="80%;">
      <div v-html="notice.noticeContent" v-if="notice && shouldShowNoticeDialog"></div>
    </el-dialog>
  </div>
</template>

<script>
  import TorrentList from "@/components/TorrentList"
  import { listCategory } from "@/api/galaxy/category";
  import { listTorrents, getNotice } from "@/api/galaxy/piazza"
  export default {
    name: "index",
    components: {
      TorrentList
    },
    data() {
      return {
        notice: null,
        menuIndex: null,
        page: {
          total: 0
        },
        queryParams: {},
        noticeQuery: {
          pageNum: 1,
          pageSize: 10,
          noticeTitle: undefined,
          createBy: undefined,
          status: undefined
        },
        categories: null,
        categoryTree: null,
        notices: null,
        shouldShowNoticeDialog: false,
        loading: false
      };
    },
    methods: {
      handleCurrentChange () {
        this.$refs.torrentList.queryParams = this.queryParams
        this.$refs.torrentList.handleCurrentChange()
      },
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
      handleMenuSelect (index) {
        if (parseInt(index) === 0) {
          index = null
        }
        this.queryParams.categories = index
        this.handleCurrentChange()
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
  #app .hideSidebar .el-submenu > .el-submenu__title {
    padding: 0 20px !important;
  }
</style>
<style scoped lang="scss">
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

