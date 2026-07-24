package main

import (
	"encoding/binary"
	"fmt"
	"hash/fnv"
	"os"
	"path/filepath"

	bolt "go.etcd.io/bbolt"
)

func main() {
	out := "."
	if len(os.Args) > 1 {
		out = os.Args[1]
	}
	must(genOverflow(filepath.Join(out, "overflow.db")))
	must(genEmptyValue(filepath.Join(out, "empty-value.db")))
	must(genSparse(filepath.Join(out, "sparse.db")))
	must(genPageSize64K(filepath.Join(out, "pagesize-64k.db")))
	fmt.Println("fixtures written to", out)
}

func must(err error) {
	if err != nil {
		panic(err)
	}
}

func pattern(size int, mod int) []byte {
	b := make([]byte, size)
	for i := range b {
		b[i] = byte(i % mod)
	}
	return b
}

func genOverflow(path string) error {
	os.Remove(path)
	db, err := bolt.Open(path, 0600, nil)
	if err != nil {
		return err
	}
	defer db.Close()
	return db.Update(func(tx *bolt.Tx) error {
		data, err := tx.CreateBucket([]byte("data"))
		if err != nil {
			return err
		}
		if err := data.Put([]byte("small"), []byte("ok")); err != nil {
			return err
		}
		if err := data.Put([]byte("large-10k"), pattern(10240, 251)); err != nil {
			return err
		}
		if err := data.Put([]byte("large-20k"), pattern(20480, 253)); err != nil {
			return err
		}
		nested, err := tx.CreateBucket([]byte("nested"))
		if err != nil {
			return err
		}
		return nested.Put([]byte("big"), pattern(8192, 241))
	})
}

func genEmptyValue(path string) error {
	os.Remove(path)
	db, err := bolt.Open(path, 0600, nil)
	if err != nil {
		return err
	}
	defer db.Close()
	return db.Update(func(tx *bolt.Tx) error {
		b, err := tx.CreateBucket([]byte("markers"))
		if err != nil {
			return err
		}
		if err := b.Put([]byte("a"), nil); err != nil {
			return err
		}
		if err := b.Put([]byte("b"), []byte{}); err != nil {
			return err
		}
		return b.Put([]byte("c"), []byte("x"))
	})
}

func genSparse(path string) error {
	os.Remove(path)
	db, err := bolt.Open(path, 0600, nil)
	if err != nil {
		return err
	}
	defer db.Close()
	return db.Update(func(tx *bolt.Tx) error {
		b, err := tx.CreateBucket([]byte("wide"))
		if err != nil {
			return err
		}
		for i := 0; i < 500; i++ {
			k := fmt.Sprintf("key-%04d", i)
			v := fmt.Sprintf("val-%04d", i)
			if err := b.Put([]byte(k), []byte(v)); err != nil {
				return err
			}
		}
		return nil
	})
}

const (
	pageHeaderSize = 16
	leafPageFlag   = 0x02
	metaPageFlag   = 0x04
	magic          = 0xED0CDAED
	version        = 2
)

// genPageSize64K writes a minimal but valid bbolt file with a 64 KiB page
// size. bbolt itself always uses the OS page size when creating a database,
// so a 64 KiB-page fixture cannot be produced through the bbolt API on a
// 4 KiB-page host; the file is synthesized byte-by-byte instead.
// Layout: page 0 = meta (txid 1), page 1 = meta (txid 2, the one readers
// should pick), page 2 = leaf with two plain key/value pairs.
func genPageSize64K(path string) error {
	const ps = 65536
	buf := make([]byte, ps*3)

	writeMeta := func(pageIdx int, txid uint64) {
		p := buf[pageIdx*ps:]
		binary.LittleEndian.PutUint64(p[0:], uint64(pageIdx))
		binary.LittleEndian.PutUint16(p[8:], metaPageFlag)
		m := p[pageHeaderSize:]
		binary.LittleEndian.PutUint32(m[0:], magic)
		binary.LittleEndian.PutUint32(m[4:], version)
		binary.LittleEndian.PutUint32(m[8:], ps)
		binary.LittleEndian.PutUint64(m[16:], 2) // root bucket pgid
		binary.LittleEndian.PutUint64(m[24:], 0) // root bucket sequence
		binary.LittleEndian.PutUint64(m[32:], 0) // freelist pgid
		binary.LittleEndian.PutUint64(m[40:], 3) // high-water pgid
		binary.LittleEndian.PutUint64(m[48:], txid)
		h := fnv.New64a()
		h.Write(m[:56])
		binary.LittleEndian.PutUint64(m[56:], h.Sum64())
	}
	writeMeta(0, 1)
	writeMeta(1, 2)

	k0, v0 := []byte("hello"), []byte("world-64k-page")
	k1, v1 := []byte("second"), []byte("value2")

	p := buf[2*ps:]
	binary.LittleEndian.PutUint64(p[0:], 2)
	binary.LittleEndian.PutUint16(p[8:], leafPageFlag)
	binary.LittleEndian.PutUint16(p[10:], 2)

	dataOff := pageHeaderSize + 2*16
	e0 := p[pageHeaderSize:]
	binary.LittleEndian.PutUint32(e0[0:], 0)
	binary.LittleEndian.PutUint32(e0[4:], uint32(dataOff-pageHeaderSize))
	binary.LittleEndian.PutUint32(e0[8:], uint32(len(k0)))
	binary.LittleEndian.PutUint32(e0[12:], uint32(len(v0)))
	e1 := p[pageHeaderSize+16:]
	binary.LittleEndian.PutUint32(e1[0:], 0)
	binary.LittleEndian.PutUint32(e1[4:], uint32(dataOff+len(k0)+len(v0)-pageHeaderSize-16))
	binary.LittleEndian.PutUint32(e1[8:], uint32(len(k1)))
	binary.LittleEndian.PutUint32(e1[12:], uint32(len(v1)))

	copy(p[dataOff:], k0)
	copy(p[dataOff+len(k0):], v0)
	copy(p[dataOff+len(k0)+len(v0):], k1)
	copy(p[dataOff+len(k0)+len(v0)+len(k1):], v1)

	return os.WriteFile(path, buf, 0644)
}
